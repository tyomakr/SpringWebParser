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
        mlPublishService.config.mockResolvedValue({
            data: { phashMaxDist: 12, grayBand: 4 },
        });
        mlPublishService.feedback.mockResolvedValue({
            data: { saved: 0 },
        });
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
                        zone: "hit",
                        hash: "abc",
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
                        zone: "gray",
                        hash: "hash2",
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
        const commitPayload = mlPublishService.commit.mock.calls[0][0];
        expect(commitPayload).toHaveLength(1);
        expect(commitPayload[0]).toEqual(
            expect.objectContaining({
                id: "2",
                publish: false,
                decision: "REVIEW",
                score: 0.5,
            })
        );
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

    it("filters uncertain recommendations", async () => {
        storeFI.webImages = [
            { id: "1", directLink: "https://example.com/img1.jpg" },
            { id: "2", directLink: "https://example.com/img2.jpg" },
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
                        zone: "gray",
                        hash: "hash-1",
                    },
                    {
                        id: "2",
                        url: "https://example.com/img2.jpg",
                        score: 0.6,
                        reason: "ok",
                        decision: "SKIP",
                        zone: "hit",
                        hash: "hash-2",
                    },
                ],
            },
        });

        render(<MlPublishPage />);
        fireEvent.click(screen.getByText("Запросить ML-превью"));
        await waitFor(() => expect(mlPublishService.preview).toHaveBeenCalled());
        fireEvent.click(screen.getByLabelText("Show uncertain only"));

        expect(screen.getByText("https://example.com/img1.jpg")).toBeInTheDocument();
        expect(screen.queryByText("https://example.com/img2.jpg")).toBeNull();
    });

    it("saves feedback payload", async () => {
        storeFI.webImages = [
            { id: "4", directLink: "https://example.com/img4.jpg" },
        ];
        mlPublishService.preview.mockResolvedValue({
            data: {
                recommendations: [
                    {
                        id: "4",
                        url: "https://example.com/img4.jpg",
                        score: 0.7,
                        reason: "cool",
                        decision: "SKIP",
                        zone: "gray",
                        hash: "hash4",
                    },
                ],
            },
        });

        render(<MlPublishPage />);
        fireEvent.click(screen.getByText("Запросить ML-превью"));
        await waitFor(() => expect(mlPublishService.preview).toHaveBeenCalled());
        fireEvent.click(screen.getByText("Save feedback"));

        await waitFor(() => expect(mlPublishService.feedback).toHaveBeenCalled());
        const payload = mlPublishService.feedback.mock.calls[0][0];
        expect(payload).toHaveLength(1);
        expect(payload[0]).toEqual(
            expect.objectContaining({
                decision: "SKIP",
                zone: "gray",
                hash: "hash4",
            })
        );
    });
});
