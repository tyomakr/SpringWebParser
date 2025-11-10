import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import VkHistoryTrainingPage from "../../pages/VkHistoryTrainingPage";
import vkHistoryService from "../../service/vkHistoryService";

jest.mock("../../service/vkHistoryService");

describe("VkHistoryTrainingPage", () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  it("loads entries and toggles flag", async () => {
    vkHistoryService.entries.mockResolvedValue({
      data: [
        { id: 1, url: "https://example.com/a.jpg", hash: "h1", useForTraining: true },
        { id: 2, url: "https://example.com/b.jpg", hash: "h2", useForTraining: false },
      ],
    });
    vkHistoryService.updateUseForTraining.mockResolvedValue({});

    render(<VkHistoryTrainingPage />);

    await waitFor(() => {
      expect(screen.getAllByRole("checkbox", { name: "use-for-training" })).toHaveLength(2);
    });

    const [first, second] = screen.getAllByRole("checkbox", { name: "use-for-training" });

    fireEvent.click(second);
    expect(vkHistoryService.updateUseForTraining).toHaveBeenCalledWith(2, true);

    fireEvent.click(first);
    expect(vkHistoryService.updateUseForTraining).toHaveBeenCalledWith(1, false);
  });

  it("only training toggle hits training endpoint", async () => {
    vkHistoryService.entries.mockResolvedValue({ data: [] });
    vkHistoryService.training.mockResolvedValue({ data: [] });

    render(<VkHistoryTrainingPage />);

    await waitFor(() => expect(vkHistoryService.entries).toHaveBeenCalled());

    const toggle = screen.getByRole("checkbox", { name: /Показывать только/i });
    fireEvent.click(toggle);

    await waitFor(() => expect(vkHistoryService.training).toHaveBeenCalled());
  });
});
