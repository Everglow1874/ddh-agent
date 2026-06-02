import type { ThemeConfig } from "antd";

// 晴空白 (Clear Sky White) theme
export const PRIMARY = "#4361ee";
export const PAGE_BG = "#f8faff";
export const CARD_BG = "#ffffff";
export const BORDER = "#e8eef8";
export const TEXT_PRIMARY = "#1a2a4a";
export const TEXT_SECONDARY = "#8a9ab8";
export const CONFIRM_BG = "#fffbec";
export const CONFIRM_BORDER = "#f0c040";

export const theme: ThemeConfig = {
  token: {
    colorPrimary: PRIMARY,
    colorBgLayout: PAGE_BG,
    colorBgContainer: CARD_BG,
    colorBorder: BORDER,
    colorText: TEXT_PRIMARY,
    colorTextSecondary: TEXT_SECONDARY,
    borderRadius: 8,
    fontSize: 14,
  },
};
