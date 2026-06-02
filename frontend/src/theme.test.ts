import { describe, it, expect } from "vitest";
import { theme, PRIMARY, CONFIRM_BORDER } from "./theme";

describe("theme", () => {
  it("uses the clear-sky-white primary color", () => {
    expect(PRIMARY).toBe("#4361ee");
    expect(theme.token?.colorPrimary).toBe("#4361ee");
  });

  it("defines the confirm card border color", () => {
    expect(CONFIRM_BORDER).toBe("#f0c040");
  });

  it("sets border radius to 8", () => {
    expect(theme.token?.borderRadius).toBe(8);
  });
});
