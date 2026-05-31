import io
import json
import pytest


VALID_CSV = b"column_name,data_type\nid,BIGINT\n"


def _make_project(client, auth_headers):
    r = client.post("/api/projects", json={"name": "TestProj"}, headers=auth_headers)
    return r.json()["id"]


def _import_table(client, auth_headers):
    return client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": ("t.csv", io.BytesIO(VALID_CSV), "text/csv")},
        headers=auth_headers,
    ).json()["id"]


def test_create_conversation(client, auth_headers):
    pid = _make_project(client, auth_headers)
    resp = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers)
    assert resp.status_code == 201
    data = resp.json()
    assert data["project_id"] == pid
    assert data["state"] == 1


def test_list_conversations(client, auth_headers):
    pid = _make_project(client, auth_headers)
    client.post(f"/api/projects/{pid}/conversations", headers=auth_headers)
    client.post(f"/api/projects/{pid}/conversations", headers=auth_headers)
    resp = client.get(f"/api/projects/{pid}/conversations", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) >= 2


def test_post_chat_saves_message(client, auth_headers):
    pid = _make_project(client, auth_headers)
    cid = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers).json()["id"]
    resp = client.post(f"/api/conversations/{cid}/chat",
                       json={"message": "analyze my data"},
                       headers=auth_headers)
    assert resp.status_code == 200
    msgs = client.get(f"/api/conversations/{cid}/messages", headers=auth_headers).json()
    assert any(m["content"] == "analyze my data" for m in msgs)


def test_confirm_schema_updates_state(client, auth_headers):
    pid = _make_project(client, auth_headers)
    cid = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers).json()["id"]
    resp = client.post(
        f"/api/conversations/{cid}/confirm-schema",
        json={"target_table": "result_tbl", "columns": [{"name": "id", "type": "BIGINT"}]},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["state"] == 3


def test_confirm_steps_updates_state(client, auth_headers):
    pid = _make_project(client, auth_headers)
    cid = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers).json()["id"]
    # Get to state 3 first via confirm-schema
    client.post(
        f"/api/conversations/{cid}/confirm-schema",
        json={"target_table": "t", "columns": [{"name": "id", "type": "INT"}]},
        headers=auth_headers,
    )
    resp = client.post(
        f"/api/conversations/{cid}/confirm-steps",
        json={"steps": [{"step_order": 1, "step_name": "load", "description": "x", "is_temp_table": False, "output_table": "t"}]},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["state"] == 4


def test_get_messages(client, auth_headers):
    pid = _make_project(client, auth_headers)
    cid = client.post(f"/api/projects/{pid}/conversations", headers=auth_headers).json()["id"]
    client.post(f"/api/conversations/{cid}/chat", json={"message": "hi"}, headers=auth_headers)
    resp = client.get(f"/api/conversations/{cid}/messages", headers=auth_headers)
    assert resp.status_code == 200
    assert isinstance(resp.json(), list)
