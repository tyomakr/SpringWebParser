import React from "react";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import VkHistoryTrainingPage from "../../pages/VkHistoryTrainingPage";
import vkHistoryService from "../../service/vkHistoryService";

jest.mock("../../service/vkHistoryService");

const originalIntersectionObserver = window.IntersectionObserver;
let intersectionCallback;

beforeAll(() => {
  window.IntersectionObserver = class {
    constructor(callback) {
      intersectionCallback = callback;
    }
    observe() {}
    disconnect() {}
  };
});

afterAll(() => {
  window.IntersectionObserver = originalIntersectionObserver;
});

const triggerIntersection = () => {
  if (intersectionCallback) {
    intersectionCallback([{ isIntersecting: true }]);
  }
};

describe("VkHistoryTrainingPage", () => {
  beforeEach(() => {
    jest.resetAllMocks();
    intersectionCallback = null;
  });

  it("loads first page and fetches more on scroll", async () => {
    const firstPage = {
      data: {
        items: [{ id: 1, hash: "h1", url: "https://example.com/1.jpg" }],
        total: 2,
        limit: 50,
        offset: 0,
      },
    };
    const secondPage = {
      data: {
        items: [{ id: 2, hash: "h2", url: "https://example.com/2.jpg" }],
        total: 2,
        limit: 50,
        offset: 1,
      },
    };

    vkHistoryService.entriesPage.mockResolvedValueOnce(firstPage).mockResolvedValueOnce(secondPage);
    vkHistoryService.updateUseForTraining.mockResolvedValue({});
    vkHistoryService.syncWall.mockResolvedValue({ data: { inserted: 0 } });

    render(<VkHistoryTrainingPage />);

    await waitFor(() => expect(vkHistoryService.entriesPage).toHaveBeenCalledTimes(1));

    act(() => {
      triggerIntersection();
    });

    await waitFor(() => expect(vkHistoryService.entriesPage).toHaveBeenCalledTimes(2));
    const images = await screen.findAllByRole("img");
    expect(images).toHaveLength(2);
  });

  it("resets pagination when training filter is applied", async () => {
    vkHistoryService.entriesPage.mockResolvedValue({
      data: { items: [], total: 0, limit: 50, offset: 0 },
    });

    render(<VkHistoryTrainingPage />);

    await waitFor(() => expect(vkHistoryService.entriesPage).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole("button", { name: "Для обучения" }));

    await waitFor(() => {
      expect(vkHistoryService.entriesPage).toHaveBeenLastCalledWith(
        expect.objectContaining({
          useForTraining: true,
          limit: 50,
          offset: 0,
        })
      );
    });
  });
});
