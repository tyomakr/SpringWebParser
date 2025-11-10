jest.mock("../../service/backendApiService", () => ({
    getWebImagesOnPages: jest.fn(),
    saveAndPublishSelectedImages: jest.fn(),
}));
jest.mock("../../service/mlPublishService");
jest.mock("axios");

import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import MlPublishPage from "../MlPublishPage";
import mlPublishService from "../../service/mlPublishService";
import storeFI from "../../store/StoreFI";

describe("MlPublishPage", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        storeFI.webImages = [];
    });

    it("renders preview and toggles publish", async () => {
        storeFI.webImages = [
            { id: "1", directLink: "https://example.com/img1.jpg" },
        ];
        mlPublishService.preview.mockResolvedValue({
            data: {
                recommendations: [
                    {
                        id: "1",
                        url: "https://example.com/img1.jpg",
                        score: 0.8,
                        reason: "hi",
                        decision: "PUBLISH",
                    },
                ],
            },
        });

        render(<MlPublishPage />);
        fireEvent.click(screen.getByText("Запросить ML-превью"));

        await waitFor(() => expect(mlPublishService.preview).toHaveBeenCalled());
        const checkbox = await screen.findByRole("checkbox");
        expect(checkbox.checked).toBeTruthy();
        fireEvent.click(checkbox);
        expect(checkbox.checked).toBeFalsy();
    });

    it("sends commit payload", async () => {
        storeFI.webImages = [
            { id: "2", directLink: "https://example.com/img2.jpg" },
        ];
        mlPublishService.preview.mockResolvedValue({
            data: {
                recommendations: [
                    {
                        id: "2",
                        url: "https://example.com/img2.jpg",
                        score: 0.5,
                        reason: "mid",
                        decision: "REVIEW",
                    },
                ],
            },
        });
        mlPublishService.commit.mockResolvedValue({
            data: { uploadedCount: 1, publishedCount: 0, postsPublished: 0, postsFailed: 1 },
        });

        render(<MlPublishPage />);
        fireEvent.click(screen.getByText("Запросить ML-превью"));

        await waitFor(() => expect(mlPublishService.preview).toHaveBeenCalled());
        const checkbox = await screen.findByRole("checkbox");
        fireEvent.click(checkbox);
        fireEvent.click(screen.getByText("Опубликовать выбранные"));

        await waitFor(() => expect(mlPublishService.commit).toHaveBeenCalled());
        expect(mlPublishService.commit).toHaveBeenCalledWith([
            {
                id: "2",
                url: "https://example.com/img2.jpg",
                score: 0.5,
                reason: "mid",
                decision: "REVIEW",
                publish: true,
            },
        ]);
    });

    it("shows error when preview fails", async () => {
        storeFI.webImages = [
            { id: "3", directLink: "https://example.com/img3.jpg" },
        ];
        mlPublishService.preview.mockRejectedValue(new Error("oops"));

        render(<MlPublishPage />);
        fireEvent.click(screen.getByText("Запросить ML-превью"));

        const alert = await screen.findByRole("alert");
        expect(alert.textContent).toContain("oops");
    });
});
