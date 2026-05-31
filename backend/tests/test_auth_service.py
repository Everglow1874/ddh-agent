import pytest
from app.services.auth_service import hash_password, verify_password, create_access_token, decode_access_token


def test_hash_password_returns_string():
    result = hash_password("mypassword")
    assert isinstance(result, str)
    assert result != "mypassword"


def test_verify_password_correct():
    hashed = hash_password("mypassword")
    assert verify_password("mypassword", hashed) is True


def test_verify_password_wrong():
    hashed = hash_password("mypassword")
    assert verify_password("wrongpassword", hashed) is False


def test_create_and_decode_token():
    token = create_access_token(user_id=42)
    payload = decode_access_token(token)
    assert payload is not None
    assert payload["sub"] == 42


def test_decode_invalid_token_returns_none():
    result = decode_access_token("not.a.valid.token")
    assert result is None
