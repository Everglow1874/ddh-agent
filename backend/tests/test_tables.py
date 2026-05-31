import io


VALID_CSV_BYTES = (
    b"column_name,data_type,comment\n"
    b"user_id,VARCHAR(64),\xe7\x94\xa8\xe6\x88\xb7ID\n"
    b'amount,"DECIMAL(18,2)",\xe9\x87\x91\xe9\xa2\x9d\n'
)


def test_import_csv_public_table(client, auth_headers):
    resp = client.post(
        "/api/tables/import",
        data={"scope": "1", "description": "订单表"},
        files={"file": ("dw_order.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data["name"] == "dw_order"
    assert data["scope"] == 1


def test_import_csv_private_table(client, auth_headers):
    resp = client.post(
        "/api/tables/import",
        data={"scope": "2"},
        files={"file": ("my_table.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    assert resp.json()["scope"] == 2


def test_import_csv_bad_format(client, auth_headers):
    bad_csv = b"wrong_col,data_type\nval,INT\n"
    resp = client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": ("bad.csv", io.BytesIO(bad_csv), "text/csv")},
        headers=auth_headers,
    )
    assert resp.status_code == 400
    assert "column_name" in resp.json()["detail"].lower()


def test_list_tables_requires_auth(client):
    resp = client.get("/api/tables")
    assert resp.status_code == 403


def test_list_tables_public(client, auth_headers):
    client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": ("t.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    resp = client.get("/api/tables?scope=public", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) >= 1


def test_get_table_detail(client, auth_headers):
    r = client.post(
        "/api/tables/import",
        data={"scope": "1"},
        files={"file": ("detail.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    table_id = r.json()["id"]
    resp = client.get(f"/api/tables/{table_id}", headers=auth_headers)
    assert resp.status_code == 200
    assert "columns" in resp.json()
    assert len(resp.json()["columns"]) == 2


def test_delete_table(client, auth_headers):
    r = client.post(
        "/api/tables/import",
        data={"scope": "2"},
        files={"file": ("del.csv", io.BytesIO(VALID_CSV_BYTES), "text/csv")},
        headers=auth_headers,
    )
    table_id = r.json()["id"]
    resp = client.delete(f"/api/tables/{table_id}", headers=auth_headers)
    assert resp.status_code == 204
    resp2 = client.get(f"/api/tables/{table_id}", headers=auth_headers)
    assert resp2.status_code == 404
