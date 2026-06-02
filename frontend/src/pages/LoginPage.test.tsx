import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { LoginPage } from "./LoginPage";
import { AuthProvider } from "../auth/AuthContext";
import * as authApi from "../api/auth";

vi.mock("../api/auth");

function renderLogin() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("renders username and password fields", () => {
    renderLogin();
    expect(screen.getByLabelText("用户名")).toBeInTheDocument();
    expect(screen.getByLabelText("密码")).toBeInTheDocument();
  });

  it("calls login API on submit", async () => {
    vi.mocked(authApi.login).mockResolvedValue({ access_token: "tok", token_type: "bearer" });
    renderLogin();
    await userEvent.type(screen.getByLabelText("用户名"), "alice");
    await userEvent.type(screen.getByLabelText("密码"), "pass123");
    await userEvent.click(screen.getByRole("button", { name: /登\s*录/ }));
    expect(authApi.login).toHaveBeenCalledWith("alice", "pass123");
  });
});
