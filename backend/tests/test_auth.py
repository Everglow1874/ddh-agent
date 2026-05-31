def test_register_success(client):
    resp = client.post("/api/auth/register", json={
        "username": "alice",
        "email": "alice@example.com",
        "password": "password123"
    })
    assert resp.status_code == 201
    data = resp.json()
    assert data["username"] == "alice"
    assert "password" not in data
    assert "password_hash" not in data


def test_register_duplicate_username(client):
    payload = {"username": "bob", "email": "bob@example.com", "password": "pass123"}
    client.post("/api/auth/register", json=payload)
    resp = client.post("/api/auth/register", json={
        "username": "bob",
        "email": "bob2@example.com",
        "password": "pass123"
    })
    assert resp.status_code == 400
    assert "username" in resp.json()["detail"].lower()


def test_register_duplicate_email(client):
    client.post("/api/auth/register", json={
        "username": "carol", "email": "carol@example.com", "password": "pass"
    })
    resp = client.post("/api/auth/register", json={
        "username": "carol2", "email": "carol@example.com", "password": "pass"
    })
    assert resp.status_code == 400
    assert "email" in resp.json()["detail"].lower()


def test_login_success(client):
    client.post("/api/auth/register", json={
        "username": "dave", "email": "dave@example.com", "password": "secret"
    })
    resp = client.post("/api/auth/login", json={
        "username": "dave", "password": "secret"
    })
    assert resp.status_code == 200
    assert "access_token" in resp.json()
    assert resp.json()["token_type"] == "bearer"


def test_login_wrong_password(client):
    client.post("/api/auth/register", json={
        "username": "eve", "email": "eve@example.com", "password": "correct"
    })
    resp = client.post("/api/auth/login", json={
        "username": "eve", "password": "wrong"
    })
    assert resp.status_code == 401


def test_login_nonexistent_user(client):
    resp = client.post("/api/auth/login", json={
        "username": "nobody", "password": "pass"
    })
    assert resp.status_code == 401
