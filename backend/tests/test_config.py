from app.config import settings

def test_settings_has_required_fields():
    assert settings.algorithm == "HS256"
    assert settings.database_url
    assert settings.projects_dir

def test_settings_database_url_format():
    assert "://" in settings.database_url
