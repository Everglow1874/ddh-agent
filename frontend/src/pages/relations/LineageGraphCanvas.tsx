import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from "react";
import { Graph } from "@antv/g6";
import type { IElementEvent, ElementDatum } from "@antv/g6";
import { toPng } from "html-to-image";
import { message } from "antd";
import { RELATION_TYPE_LABELS } from "../../api/types";
import type { LineageGraph, GraphNode, GraphEdge } from "../../api/types";

/* ============================================================================
 * 表血缘图画布 —— 浅色 ER 风格
 * 设计语言：白底卡片 + 细描边 + 柔和投影；表头列表名，展开后逐行列字段，
 * 关系连线锚定到具体字段行；点击表高亮上下游链路并淡化无关节点；悬浮看血缘说明。
 * G6 v5 · HTML 节点 + 字段级 port。两个弹窗复用此画布。
 * ========================================================================== */

export interface LineageGraphCanvasHandle {
  exportImage: () => Promise<void>;
  exportJson: () => void;
  fitView: () => void;
}

interface Props {
  data: LineageGraph | null;
  loading?: boolean;
  height?: number | string;
  /** 连线模式：点击两张表触发 onConnectPair */
  lineMode?: boolean;
  onConnectPair?: (source: GraphNode, target: GraphNode, existing?: GraphEdge) => void;
  /** 点击已有关系连线 */
  onEditEdge?: (edge: GraphEdge, source: GraphNode, target: GraphNode) => void;
}

/* --- 视觉常量 ----------------------------------------------------------- */
const CARD_W = 252;
const HEAD_H = 46;
const ROW_H = 28;
const MAX_ROWS = 16;
const PAD_BODY = 6; // 字段区上下内边距合计

const REL_COLOR: Record<string, string> = {
  ONE_TO_ONE: "#4f6bed",
  ONE_TO_MANY: "#2ea98c",
  MANY_TO_ONE: "#e0892b",
  MANY_TO_MANY: "#b5559e",
};

type EdgeMode = "normal" | "lit" | "dim";

const edgeColorOf = (type?: string) => REL_COLOR[type ?? ""] || "#9aa6b6";

/**
 * 边样式数据驱动：颜色 / 粗细 / 淡化全部由每条边 data 里的 relationType + hl 计算。
 * 用 spec 级动态函数（而非每条边的静态 style.stroke），彻底避免全局默认色覆盖单边颜色，
 * 保证一对一=靛蓝、一对多=青绿…… 与图例严格一致。
 */
type EdgeDatum = { data?: { relationType?: string; hl?: EdgeMode; withLabel?: boolean } };
const EDGE_SPEC_STYLE = {
  stroke: (d: EdgeDatum) => edgeColorOf(d.data?.relationType),
  strokeOpacity: (d: EdgeDatum) => (d.data?.hl === "dim" ? 0.16 : 1),
  lineWidth: (d: EdgeDatum) => (d.data?.hl === "lit" ? 2.6 : 1.8),
  endArrow: true,
  endArrowType: "vee",
  endArrowSize: 8,
  endArrowFill: (d: EdgeDatum) => edgeColorOf(d.data?.relationType),
  endArrowOpacity: (d: EdgeDatum) => (d.data?.hl === "dim" ? 0.16 : 1),
  labelText: (d: EdgeDatum) =>
    d.data?.withLabel ? RELATION_TYPE_LABELS[d.data?.relationType ?? ""] || d.data?.relationType || "" : "",
  labelFill: (d: EdgeDatum) => edgeColorOf(d.data?.relationType),
  labelFillOpacity: (d: EdgeDatum) => (d.data?.hl === "dim" ? 0.3 : 1),
  labelFontSize: 10.5,
  labelFontFamily: "Manrope, system-ui, sans-serif",
  labelBackground: true,
  labelBackgroundFill: "#ffffff",
  labelBackgroundStroke: "#eef1f6",
  labelBackgroundLineWidth: 1,
  labelBackgroundRadius: 6,
  labelPadding: [2, 6],
};

const FONT_LINK_ID = "lineage-font-link";
const STYLE_ID = "lineage-canvas-style";

function ensureAssets() {
  if (typeof document === "undefined") return;
  if (!document.getElementById(FONT_LINK_ID)) {
    const link = document.createElement("link");
    link.id = FONT_LINK_ID;
    link.rel = "stylesheet";
    link.href =
      "https://fonts.googleapis.com/css2?family=Manrope:wght@500;600;700;800&family=IBM+Plex+Mono:wght@400;500&display=swap";
    document.head.appendChild(link);
  }
  let styleEl = document.getElementById(STYLE_ID) as HTMLStyleElement | null;
  if (!styleEl) {
    styleEl = document.createElement("style");
    styleEl.id = STYLE_ID;
    document.head.appendChild(styleEl);
  }
  // 始终用最新内容覆盖，避免旧样式残留（同一会话内多次改版时尤其重要）
  styleEl.textContent = CANVAS_CSS;
}

const esc = (s: string) =>
  String(s ?? "").replace(/[&<>"]/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c] as string),
  );

type NodeState = "normal" | "active" | "lit" | "dim";

function nodeHeight(node: GraphNode, collapsed: boolean): number {
  if (collapsed) return HEAD_H;
  const rows = Math.min(node.columns.length, MAX_ROWS) + (node.columns.length > MAX_ROWS ? 1 : 0);
  return HEAD_H + rows * ROW_H + PAD_BODY;
}

/** 卡片 HTML —— 根据折叠 / 高亮状态生成 */
function cardHtml(
  node: GraphNode,
  collapsed: boolean,
  relCols: Set<number>,
  state: NodeState,
): string {
  const stateCls =
    state === "active" ? " is-active" : state === "lit" ? " is-lit" : state === "dim" ? " is-dim" : "";
  const chevron = collapsed ? "▸" : "▾";
  const head = `<div class="lin-hd">
      <span class="lin-io">${chevron}</span>
      <span class="lin-tdot"></span>
      <span class="lin-htx">
        <span class="lin-nm" title="${esc(node.table_name)}">${esc(node.table_name)}</span>
        ${node.table_comment ? `<span class="lin-cm">${esc(node.table_comment)}</span>` : ""}
      </span>
      <span class="lin-cnt">${node.columns.length}</span>
    </div>`;

  if (collapsed) {
    return `<div class="lin-card is-collapsed${stateCls}">${head}</div>`;
  }

  const visible = node.columns.slice(0, MAX_ROWS);
  const rows = visible
    .map((c) => {
      const rel = relCols.has(c.id);
      const title = `${c.name}${c.comment ? "　" + c.comment : ""}${c.type ? "　" + c.type : ""}`;
      return `<div class="lin-f${rel ? " is-rel" : ""}" title="${esc(title)}">
          <span class="lin-fn">${rel ? '<i class="lin-fdot"></i>' : ""}${esc(c.name)}</span>
          <span class="lin-fc">${esc(c.comment || "")}</span>
          <span class="lin-ft">${esc(c.type || "")}</span>
        </div>`;
    })
    .join("");
  const more =
    node.columns.length > MAX_ROWS
      ? `<div class="lin-more">＋${node.columns.length - MAX_ROWS} 个字段</div>`
      : "";
  return `<div class="lin-card${stateCls}">${head}<div class="lin-fs">${rows}${more}</div></div>`;
}

/** 字段在卡片内的纵向比例（用于 port 定位） */
function fieldPortY(node: GraphNode, colId: number, totalH: number): number | null {
  const idx = node.columns.findIndex((c) => c.id === colId);
  if (idx < 0 || idx >= MAX_ROWS) return null;
  return (HEAD_H + idx * ROW_H + ROW_H / 2 + PAD_BODY / 2) / totalH;
}

function buildPorts(node: GraphNode, collapsed: boolean, totalH: number) {
  const ports: { key: string; placement: [number, number]; r: number }[] = [
    { key: "L", placement: [0, HEAD_H / 2 / totalH], r: 0 },
    { key: "R", placement: [1, HEAD_H / 2 / totalH], r: 0 },
  ];
  if (!collapsed) {
    node.columns.slice(0, MAX_ROWS).forEach((c) => {
      const y = fieldPortY(node, c.id, totalH)!;
      ports.push({ key: `L:${c.id}`, placement: [0, y], r: 0 });
      ports.push({ key: `R:${c.id}`, placement: [1, y], r: 0 });
    });
  }
  return ports;
}

export const LineageGraphCanvas = forwardRef<LineageGraphCanvasHandle, Props>(
  ({ data, loading, height = "70vh", lineMode = false, onConnectPair, onEditEdge }, ref) => {
    const wrapRef = useRef<HTMLDivElement>(null);
    const hostRef = useRef<HTMLDivElement>(null);
    const ctrlRef = useRef<HTMLDivElement>(null);
    const graphRef = useRef<Graph | null>(null);

    const nodeMapRef = useRef<Map<string, GraphNode>>(new Map());
    const relColsRef = useRef<Map<string, Set<number>>>(new Map());
    const collapsedRef = useRef<Set<string>>(new Set());
    const focusRef = useRef<string | null>(null);
    const pickRef = useRef<string | null>(null);
    const lineModeRef = useRef(lineMode);

    const [allCollapsed, setAllCollapsed] = useState(false);

    useEffect(() => {
      lineModeRef.current = lineMode;
      // 切换连线模式时清空选择
      pickRef.current = null;
      focusRef.current = null;
      repaint();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [lineMode]);

    useEffect(() => {
      ensureAssets();
    }, []);

    /* --- 计算节点高亮状态 ------------------------------------------------ */
    const computeStates = (): Map<string, NodeState> => {
      const states = new Map<string, NodeState>();
      const ids = [...nodeMapRef.current.keys()];
      const focus = focusRef.current;
      const pick = pickRef.current;

      if (lineModeRef.current) {
        ids.forEach((id) => states.set(id, pick ? (id === pick ? "active" : "dim") : "normal"));
        return states;
      }
      if (!focus) {
        ids.forEach((id) => states.set(id, "normal"));
        return states;
      }
      // 沿边双向 BFS，得到上下游链路
      const adj = new Map<string, Set<string>>();
      ids.forEach((id) => adj.set(id, new Set()));
      (data?.edges ?? []).forEach((e) => {
        adj.get(e.source)?.add(e.target);
        adj.get(e.target)?.add(e.source);
      });
      const chain = new Set<string>([focus]);
      const queue = [focus];
      while (queue.length) {
        const cur = queue.shift()!;
        adj.get(cur)?.forEach((nb) => {
          if (!chain.has(nb)) {
            chain.add(nb);
            queue.push(nb);
          }
        });
      }
      ids.forEach((id) =>
        states.set(id, id === focus ? "active" : chain.has(id) ? "lit" : "dim"),
      );
      return states;
    };

    /* --- 重绘节点 / 边样式（不重新布局） -------------------------------- */
    const repaint = () => {
      const graph = graphRef.current;
      if (!graph) return;
      const states = computeStates();
      const focusActive = !!focusRef.current && !lineModeRef.current;

      const nodeUpdates: Array<Record<string, unknown>> = [];
      nodeMapRef.current.forEach((node, id) => {
        const collapsed = collapsedRef.current.has(id);
        const h = nodeHeight(node, collapsed);
        nodeUpdates.push({
          id,
          style: {
            size: [CARD_W, h],
            innerHTML: cardHtml(node, collapsed, relColsRef.current.get(id) ?? new Set(), states.get(id) ?? "normal"),
            ports: buildPorts(node, collapsed, h),
          },
        });
      });

      const edgeUpdates: Array<Record<string, unknown>> = [];
      (data?.edges ?? []).forEach((e, ei) => {
        const srcNode = nodeMapRef.current.get(e.source);
        const tgtNode = nodeMapRef.current.get(e.target);
        if (!srcNode || !tgtNode) return;
        const srcCollapsed = collapsedRef.current.has(e.source);
        const tgtCollapsed = collapsedRef.current.has(e.target);
        const pairs = e.column_pairs?.length ? e.column_pairs : [null];
        const lit =
          focusActive &&
          (states.get(e.source) === "active" || states.get(e.source) === "lit") &&
          (states.get(e.target) === "active" || states.get(e.target) === "lit");
        const mode: EdgeMode = !focusActive ? "normal" : lit ? "lit" : "dim";

        pairs.forEach((p, pi) => {
          const sPort =
            !srcCollapsed && p && fieldPortY(srcNode, p.source_column_id, 1) !== null
              ? `R:${p.source_column_id}`
              : "R";
          const tPort =
            !tgtCollapsed && p && fieldPortY(tgtNode, p.target_column_id, 1) !== null
              ? `L:${p.target_column_id}`
              : "L";
          edgeUpdates.push({
            id: `e${ei}_${pi}`,
            data: { relationType: e.relation_type, hl: mode, withLabel: pi === 0 },
            style: { sourcePort: sPort, targetPort: tPort },
          });
        });
      });

      if (nodeUpdates.length) graph.updateNodeData(nodeUpdates as never);
      if (edgeUpdates.length) graph.updateEdgeData(edgeUpdates as never);
      graph.draw();
    };

    /* --- 构建并渲染图 --------------------------------------------------- */
    useEffect(() => {
      if (!hostRef.current || !data || data.nodes.length === 0) return;
      let disposed = false;

      // 缓存结构
      const nodeMap = new Map<string, GraphNode>();
      data.nodes.forEach((n) => nodeMap.set(n.id, n));
      nodeMapRef.current = nodeMap;

      // 每个表参与关系的字段集合（用于行高亮）
      const relCols = new Map<string, Set<number>>();
      data.nodes.forEach((n) => relCols.set(n.id, new Set()));
      data.edges.forEach((e) => {
        e.column_pairs?.forEach((p) => {
          relCols.get(e.source)?.add(p.source_column_id);
          relCols.get(e.target)?.add(p.target_column_id);
        });
      });
      relColsRef.current = relCols;

      const collapsed = collapsedRef.current;
      focusRef.current = null;
      pickRef.current = null;

      const g6Nodes = data.nodes.map((n) => {
        const isCol = collapsed.has(n.id);
        const h = nodeHeight(n, isCol);
        return {
          id: n.id,
          style: {
            size: [CARD_W, h],
            innerHTML: cardHtml(n, isCol, relCols.get(n.id) ?? new Set(), "normal"),
            ports: buildPorts(n, isCol, h),
          },
        };
      });

      const g6Edges: Array<Record<string, unknown>> = [];
      data.edges.forEach((e, ei) => {
        const srcNode = nodeMap.get(e.source);
        const tgtNode = nodeMap.get(e.target);
        if (!srcNode || !tgtNode) return;
        const pairs = e.column_pairs?.length ? e.column_pairs : [null];
        pairs.forEach((p, pi) => {
          const sPort = p ? `R:${p.source_column_id}` : "R";
          const tPort = p ? `L:${p.target_column_id}` : "L";
          g6Edges.push({
            id: `e${ei}_${pi}`,
            source: e.source,
            target: e.target,
            data: { relationType: e.relation_type, hl: "normal", withLabel: pi === 0 },
            style: { sourcePort: sPort, targetPort: tPort },
          });
        });
      });

      const graph = new Graph({
        container: hostRef.current,
        autoFit: "view",
        padding: 24,
        data: { nodes: g6Nodes as never, edges: g6Edges as never },
        node: { type: "html" },
        edge: { type: "cubic-horizontal", style: EDGE_SPEC_STYLE } as never,
        layout: {
          type: "dagre",
          rankdir: "LR",
          align: "UL",
          nodesep: 26,
          ranksep: 96,
        },
        behaviors: ["drag-canvas", "zoom-canvas", "drag-element"],
        plugins: [
          {
            type: "tooltip",
            key: "tooltip",
            trigger: "hover",
            enable: true,
            getContent: (_e: IElementEvent, items: ElementDatum[]) => {
              const raw = items?.[0]?.id;
              const id = raw != null ? String(raw) : undefined;
              if (!id) return Promise.resolve("");
              const node = nodeMapRef.current.get(id);
              if (node) return Promise.resolve(nodeTooltip(node, data));
              return Promise.resolve(edgeTooltip(id, data, nodeMapRef.current));
            },
          },
        ],
      });
      graphRef.current = graph;

      graph.render().then(() => {
        if (disposed) return;

        graph.on("node:click", (evt: IElementEvent) => {
          const id = (evt.target as unknown as { id?: string })?.id;
          if (!id || !nodeMapRef.current.has(id)) return;

          if (lineModeRef.current) {
            const first = pickRef.current;
            if (!first) {
              pickRef.current = id;
              repaint();
            } else if (first === id) {
              pickRef.current = null;
              repaint();
            } else {
              const src = nodeMapRef.current.get(first);
              const tgt = nodeMapRef.current.get(id);
              pickRef.current = null;
              repaint();
              if (src && tgt) {
                const existing = data.edges.find(
                  (e) =>
                    (e.source === first && e.target === id) ||
                    (e.source === id && e.target === first),
                );
                onConnectPair?.(src, tgt, existing);
              }
            }
            return;
          }
          focusRef.current = focusRef.current === id ? null : id;
          repaint();
        });

        graph.on("node:dblclick", (evt: IElementEvent) => {
          const id = (evt.target as unknown as { id?: string })?.id;
          if (!id || !nodeMapRef.current.has(id)) return;
          if (collapsedRef.current.has(id)) collapsedRef.current.delete(id);
          else collapsedRef.current.add(id);
          repaint();
        });

        graph.on("edge:click", (evt: IElementEvent) => {
          if (lineModeRef.current) return;
          const eid = (evt.target as unknown as { id?: string })?.id;
          if (!eid) return;
          const ei = Number(eid.replace(/^e/, "").split("_")[0]);
          const info = data.edges[ei];
          if (!info) return;
          const src = nodeMapRef.current.get(info.source);
          const tgt = nodeMapRef.current.get(info.target);
          if (src && tgt) onEditEdge?.(info, src, tgt);
        });

        graph.on("canvas:click", () => {
          if (focusRef.current || pickRef.current) {
            focusRef.current = null;
            pickRef.current = null;
            repaint();
          }
        });
      });

      return () => {
        disposed = true;
        graph.destroy();
        graphRef.current = null;
      };
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [data]);

    /* --- 工具栏动作 ----------------------------------------------------- */
    const zoomBy = (factor: number) => {
      const g = graphRef.current;
      if (!g) return;
      try {
        g.zoomTo(g.getZoom() * factor);
      } catch {
        /* noop */
      }
    };

    const doFit = () => {
      try {
        graphRef.current?.fitView();
      } catch {
        /* noop */
      }
    };

    const toggleAll = () => {
      const next = !allCollapsed;
      setAllCollapsed(next);
      const set = collapsedRef.current;
      set.clear();
      if (next) nodeMapRef.current.forEach((_v, id) => set.add(id));
      repaint();
      setTimeout(doFit, 60);
    };

    useImperativeHandle(ref, () => ({
      exportImage: async () => {
        // HTML 节点是真实 DOM，不在 G6 画布上，graph.toDataURL 截不到卡片，
        // 改用 html-to-image 截取整个图容器（卡片 + 连线一并捕获）。
        const root = wrapRef.current;
        const g = graphRef.current;
        if (!root || !g) return;
        const ctrl = ctrlRef.current;
        try {
          g.fitView();
          await new Promise((r) => setTimeout(r, 220));
          if (ctrl) ctrl.style.visibility = "hidden";
          const url = await toPng(root, {
            backgroundColor: "#ffffff",
            pixelRatio: 2,
            cacheBust: true,
            skipFonts: true,
          });
          if (ctrl) ctrl.style.visibility = "";
          const a = document.createElement("a");
          a.href = url;
          a.download = "表血缘图.png";
          a.click();
        } catch {
          if (ctrl) ctrl.style.visibility = "";
          message.error("导出图片失败");
        }
      },
      exportJson: () => {
        if (!data) return;
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "表血缘图.json";
        a.click();
        URL.revokeObjectURL(url);
      },
      fitView: doFit,
    }));

    return (
      <div className="lin-root" ref={wrapRef} style={{ height, position: "relative", width: "100%" }}>
        <div className="lin-host" ref={hostRef} style={{ width: "100%", height: "100%" }} />

        {/* 图例 */}
        <div className="lin-legend">
          <div className="lin-legend__title">关系类型</div>
          {Object.entries(RELATION_TYPE_LABELS).map(([k, label]) => (
            <div className="lin-legend__row" key={k}>
              <span className="lin-legend__line" style={{ background: REL_COLOR[k] }} />
              <span>{label}</span>
            </div>
          ))}
          <div className="lin-legend__hint">单击表高亮链路 · 双击折叠/展开字段</div>
        </div>

        {/* 浮动控制 */}
        <div className="lin-ctrl" ref={ctrlRef}>
          <button className="lin-ctrl__btn" title="放大" onClick={() => zoomBy(1.2)}>＋</button>
          <button className="lin-ctrl__btn" title="缩小" onClick={() => zoomBy(1 / 1.2)}>－</button>
          <button className="lin-ctrl__btn" title="适应画布" onClick={doFit}>⤢</button>
          <span className="lin-ctrl__sep" />
          <button className="lin-ctrl__btn lin-ctrl__btn--wide" onClick={toggleAll}>
            {allCollapsed ? "展开字段" : "收起字段"}
          </button>
        </div>

        {loading && <div className="lin-loading">加载中…</div>}
      </div>
    );
  },
);

LineageGraphCanvas.displayName = "LineageGraphCanvas";

/* --- 悬浮内容 ------------------------------------------------------------ */
function nodeTooltip(node: GraphNode, data: LineageGraph): string {
  const rels = data.edges.filter((e) => e.source === node.id || e.target === node.id);
  const colName = (nid: string, cid: number) =>
    data.nodes.find((n) => n.id === nid)?.columns.find((c) => c.id === cid)?.name ?? cid;
  const relHtml = rels.length
    ? rels
        .map((e) => {
          const other = e.source === node.id ? e.target : e.source;
          const otherName = data.nodes.find((n) => n.id === other)?.table_name ?? other;
          const dirArrow = e.source === node.id ? "→" : "←";
          const pairs = (e.column_pairs ?? [])
            .map((p) => `${colName(e.source, p.source_column_id)} = ${colName(e.target, p.target_column_id)}`)
            .join(" , ");
          return `<div class="lt-rel"><span class="lt-rel__arrow">${dirArrow}</span><b>${esc(String(otherName))}</b>
            <span class="lt-rel__type">${RELATION_TYPE_LABELS[e.relation_type] || e.relation_type}</span>
            ${pairs ? `<div class="lt-rel__pairs">${esc(pairs)}</div>` : ""}</div>`;
        })
        .join("")
    : `<div class="lt-empty">暂无关联关系</div>`;
  return `<div class="lt">
      <div class="lt-hd"><span class="lt-dot"></span><b>${esc(node.table_name)}</b>${
        node.table_comment ? `<span class="lt-cm">${esc(node.table_comment)}</span>` : ""
      }</div>
      <div class="lt-meta">${node.columns.length} 个字段</div>
      <div class="lt-sec">血缘关系</div>
      ${relHtml}
    </div>`;
}

function edgeTooltip(edgeId: string, data: LineageGraph, nodeMap: Map<string, GraphNode>): string {
  const ei = Number(edgeId.replace(/^e/, "").split("_")[0]);
  const e = data.edges[ei];
  if (!e) return "";
  const src = nodeMap.get(e.source);
  const tgt = nodeMap.get(e.target);
  const colName = (n: GraphNode | undefined, cid: number) =>
    n?.columns.find((c) => c.id === cid)?.name ?? cid;
  const pairs = (e.column_pairs ?? [])
    .map(
      (p) =>
        `<div class="lt-pair"><span>${esc(String(colName(src, p.source_column_id)))}</span><i>=</i><span>${esc(
          String(colName(tgt, p.target_column_id)),
        )}</span></div>`,
    )
    .join("");
  return `<div class="lt">
      <div class="lt-hd"><b>${esc(src?.table_name ?? "")}</b><span class="lt-rel__type">${
        RELATION_TYPE_LABELS[e.relation_type] || e.relation_type
      }</span><b>${esc(tgt?.table_name ?? "")}</b></div>
      <div class="lt-sec">关联字段</div>
      ${pairs || '<div class="lt-empty">未配置字段映射</div>'}
    </div>`;
}

/* --- 样式 ---------------------------------------------------------------- */
const CANVAS_CSS = `
.lin-root{
  --lin-accent:#4f6bed; --lin-ink:#1e2940; --lin-muted:#8a97ab;
  --lin-hair:#e8ecf3; --lin-soft:#eef1fe;
  background:
    radial-gradient(circle at 1px 1px, #e4e9f2 1px, transparent 0) 0 0 / 22px 22px,
    linear-gradient(180deg,#fbfcfe 0%, #f6f8fc 100%);
  border-radius:12px; overflow:hidden;
}
.lin-host{ position:absolute; inset:0; }

/* 卡片 */
.lin-card{
  width:100%; height:100%; box-sizing:border-box;
  font-family:'Manrope',system-ui,-apple-system,'Segoe UI',sans-serif;
  background:#fff; border:1px solid var(--lin-hair); border-radius:12px;
  box-shadow:0 6px 20px -10px rgba(30,46,90,.20), 0 1px 2px rgba(30,46,90,.06);
  overflow:hidden; transition:box-shadow .18s ease, border-color .18s ease, opacity .18s ease, transform .18s ease;
  cursor:pointer; user-select:none;
}
.lin-card.is-active{ border-color:var(--lin-accent);
  box-shadow:0 0 0 3px rgba(79,107,237,.16), 0 14px 30px -10px rgba(79,107,237,.40); transform:translateY(-1px); }
.lin-card.is-lit{ border-color:#b9c4f5; box-shadow:0 8px 22px -10px rgba(79,107,237,.28); }
.lin-card.is-dim{ opacity:.34; filter:saturate(.55); }

.lin-hd{
  display:flex; align-items:center; gap:7px; height:${HEAD_H}px; padding:0 12px;
  background:linear-gradient(180deg,#ffffff 0%, #f7f9fe 100%);
  border-bottom:1px solid var(--lin-hair);
}
.lin-io{ color:#b3bccd; font-size:10px; width:10px; flex:none; }
.lin-tdot{ width:8px; height:8px; border-radius:3px; flex:none;
  background:linear-gradient(135deg,var(--lin-accent),#7b91f4); box-shadow:0 0 0 3px rgba(79,107,237,.12); }
.lin-htx{ display:flex; flex-direction:column; justify-content:center; gap:1px; flex:1; min-width:0; }
.lin-nm{ font-weight:800; font-size:13px; color:var(--lin-ink); letter-spacing:.2px;
  white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:100%; }
.lin-cm{ font-size:11px; color:var(--lin-muted); white-space:nowrap; overflow:hidden;
  text-overflow:ellipsis; max-width:100%; }
.lin-cnt{ margin-left:auto; flex:none; font-size:10px; font-weight:700; color:#7c89a0;
  background:#eef1f6; border-radius:8px; padding:1px 7px; }
.lin-card.is-collapsed .lin-hd{ border-bottom:none; }

.lin-fs{ padding:3px 0; }
.lin-f{
  display:flex; align-items:center; gap:8px; flex-wrap:nowrap;
  height:${ROW_H}px; padding:0 12px; font-size:12px; box-sizing:border-box;
  border-left:2px solid transparent; overflow:hidden;
}
.lin-f + .lin-f{ border-top:1px dashed #f1f4f9; }
.lin-f.is-rel{ background:var(--lin-soft); border-left-color:var(--lin-accent); }
.lin-fn{ flex:0 1 auto; min-width:0; max-width:50%; color:#3a465b; font-weight:600;
  white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.lin-f.is-rel .lin-fn{ color:#2f3e8c; }
.lin-fdot{ display:inline-block; width:5px; height:5px; border-radius:50%;
  background:var(--lin-accent); margin-right:6px; vertical-align:middle; }
.lin-fc{ flex:1 1 auto; min-width:0; text-align:right; white-space:nowrap; overflow:hidden;
  text-overflow:ellipsis; color:#9aa6b6; font-size:11px; }
.lin-ft{ flex:0 0 auto; color:#9aa6b6; font-family:'IBM Plex Mono',ui-monospace,monospace;
  font-size:10.5px; font-weight:500; white-space:nowrap; }
.lin-more{ padding:5px 12px; font-size:11px; color:#9aa6b6; font-style:italic; }

/* 图例 */
.lin-legend{
  position:absolute; left:14px; bottom:14px; z-index:5;
  background:rgba(255,255,255,.86); backdrop-filter:blur(8px);
  border:1px solid var(--lin-hair); border-radius:11px; padding:10px 12px;
  font-family:'Manrope',system-ui,sans-serif; box-shadow:0 8px 24px -12px rgba(30,46,90,.22);
}
.lin-legend__title{ font-size:10px; font-weight:800; letter-spacing:.8px; text-transform:uppercase;
  color:#9aa6b6; margin-bottom:7px; }
.lin-legend__row{ display:flex; align-items:center; gap:8px; font-size:12px; color:#41506a;
  font-weight:600; margin-bottom:4px; }
.lin-legend__line{ width:16px; height:3px; border-radius:2px; flex:none; }
.lin-legend__hint{ margin-top:7px; padding-top:7px; border-top:1px dashed #e9edf4;
  font-size:10.5px; color:#9aa6b6; max-width:170px; line-height:1.5; }

/* 浮动控制 */
.lin-ctrl{
  position:absolute; right:14px; bottom:14px; z-index:5; display:flex; align-items:center; gap:4px;
  background:rgba(255,255,255,.86); backdrop-filter:blur(8px);
  border:1px solid var(--lin-hair); border-radius:11px; padding:5px;
  box-shadow:0 8px 24px -12px rgba(30,46,90,.22);
}
.lin-ctrl__btn{
  border:none; background:transparent; cursor:pointer; color:#41506a;
  font-family:'Manrope',system-ui,sans-serif; font-size:14px; font-weight:700;
  width:30px; height:30px; border-radius:8px; transition:background .15s ease, color .15s ease;
  display:inline-flex; align-items:center; justify-content:center;
}
.lin-ctrl__btn:hover{ background:var(--lin-soft); color:var(--lin-accent); }
.lin-ctrl__btn--wide{ width:auto; padding:0 12px; font-size:12px; }
.lin-ctrl__sep{ width:1px; height:18px; background:var(--lin-hair); margin:0 2px; }

.lin-loading{
  position:absolute; inset:0; display:flex; align-items:center; justify-content:center;
  color:#9aa6b6; font-family:'Manrope',system-ui,sans-serif; font-size:13px;
  background:rgba(250,251,253,.6); z-index:6;
}

/* 悬浮卡 */
.lt{ font-family:'Manrope',system-ui,sans-serif; min-width:200px; max-width:300px; }
.lt-hd{ display:flex; align-items:center; gap:7px; font-size:13px; color:#1e2940; flex-wrap:wrap; }
.lt-dot{ width:8px; height:8px; border-radius:3px; background:linear-gradient(135deg,#4f6bed,#7b91f4); flex:none; }
.lt-cm{ font-size:11px; color:#8a97ab; font-weight:500; }
.lt-meta{ font-size:11px; color:#8a97ab; margin-top:3px; }
.lt-sec{ font-size:10px; font-weight:800; letter-spacing:.8px; text-transform:uppercase;
  color:#aeb8c7; margin:9px 0 5px; }
.lt-rel{ font-size:12px; color:#41506a; padding:4px 0; border-top:1px dashed #eef1f6; }
.lt-rel__arrow{ color:#4f6bed; font-weight:800; margin-right:5px; }
.lt-rel__type{ font-size:10px; font-weight:700; color:#4f6bed; background:#eef1fe;
  border-radius:6px; padding:1px 6px; margin-left:6px; }
.lt-rel__pairs{ font-family:'IBM Plex Mono',monospace; font-size:10.5px; color:#7c89a0; margin-top:3px; }
.lt-pair{ display:flex; align-items:center; gap:8px; font-family:'IBM Plex Mono',monospace;
  font-size:11.5px; color:#3a465b; padding:2px 0; }
.lt-pair i{ color:#4f6bed; font-style:normal; font-weight:700; }
.lt-empty{ font-size:11.5px; color:#aeb8c7; }
`;
