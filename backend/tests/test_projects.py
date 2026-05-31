import io

VALID_CSV = (
    b"column_name,data_type,comment\n"
    b"id,BIGINT,\xe4\xb8\xbb\xe9\x94\xae\n"
)


def _import_table(client, auth_headers, name="src_table"):
    return client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": (f"{name}.csv", io.BytesIO(VALID_CSV), "text/csv")},
        headers=auth_headers,
    ).json()["id"]


def test_create_project(client, auth_headers):
    resp = client.post("/api/projects", json={
        "name": "用户月度统计",
        "description": "统计每月消费"
    }, headers=auth_headers)
    assert resp.status_code == 201
    data = resp.json()
    assert data["name"] == "用户月度统计"
    assert data["status"] == 1


def test_list_projects(client, auth_headers):
    client.post("/api/projects", json={"name": "P1"}, headers=auth_headers)
    client.post("/api/projects", json={"name": "P2"}, headers=auth_headers)
    resp = client.get("/api/projects", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) >= 2


def test_get_project(client, auth_headers):
    r = client.post("/api/projects", json={"name": "Detail"}, headers=auth_headers)
    pid = r.json()["id"]
    resp = client.get(f"/api/projects/{pid}", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["id"] == pid


def test_update_project(client, auth_headers):
    r = client.post("/api/projects", json={"name": "Old"}, headers=auth_headers)
    pid = r.json()["id"]
    resp = client.put(f"/api/projects/{pid}", json={"name": "New"}, headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["name"] == "New"


def test_associate_table_with_project(client, auth_headers):
    pid = client.post("/api/projects", json={"name": "Proj"}, headers=auth_headers).json()["id"]
    tid = _import_table(client, auth_headers)
    resp = client.post(f"/api/projects/{pid}/tables", json={"table_ids": [tid]}, headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["associated"] == 1


def test_remove_table_from_project(client, auth_headers):
    pid = client.post("/api/projects", json={"name": "Proj2"}, headers=auth_headers).json()["id"]
    tid = _import_table(client, auth_headers, "t2")
    client.post(f"/api/projects/{pid}/tables", json={"table_ids": [tid]}, headers=auth_headers)
    resp = client.delete(f"/api/projects/{pid}/tables/{tid}", headers=auth_headers)
    assert resp.status_code == 204


def test_delete_project(client, auth_headers):
    r = client.post("/api/projects", json={"name": "ToDelete"}, headers=auth_headers)
    pid = r.json()["id"]
    resp = client.delete(f"/api/projects/{pid}", headers=auth_headers)
    assert resp.status_code == 204
    resp2 = client.get(f"/api/projects/{pid}", headers=auth_headers)
    assert resp2.status_code == 404
