import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import { AuthProvider, useAuth } from "./AuthContext";

function Probe() {
  const { isAuthenticated, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="status">{isAuthenticated ? "in" : "out"}</span>
      <button onClick={() => login("tok123")}>login</button>
      <button onClick={() => logout()}>logout</button>
    </div>
  );
}

describe("AuthContext", () => {
  beforeEach(() => localStorage.clear());

  it("starts logged out when no token", () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );
    expect(screen.getByTestId("status").textContent).toBe("out");
  });

  it("logs in and persists token", () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );
    act(() => screen.getByText("login").click());
    expect(screen.getByTestId("status").textContent).toBe("in");
    expect(localStorage.getItem("ddh_token")).toBe("tok123");
  });

  it("logs out and clears token", () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );
    act(() => screen.getByText("login").click());
    act(() => screen.getByText("logout").click());
    expect(screen.getByTestId("status").textContent).toBe("out");
    expect(localStorage.getItem("ddh_token")).toBeNull();
  });
});
