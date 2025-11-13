jest.mock("../../service/backendApiService", () => ({
    getWebImagesOnPages: jest.fn(),
    saveAndPublishSelectedImages: jest.fn(),
}));
jest.mock("../../service/mlPublishService");

import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MlPublishPage from "../MlPublishPage";
import mlPublishService from "../../service/mlPublishService";
import storeFI from "../../store/StoreFI";

describe("MlPublishPage diagnostics", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        storeFI.webImages = [
            { id: "1", directLink: "https://example.com/img1.jpg" },
        ];
        mlPublishService.config.mockResolvedValue({
            data: {
                apiKeyConfigured: true,
                requireApiKey: false,
                maxBatchSize: 100,
                mlServiceConfig: { phashMaxDist: 12, grayBand: 4 },
            },
        });
        mlPublishService.feedback.mockResolvedValue({ data: { saved: 0 } });
    });

    it("shows unreachable banner when status returns unreachable", async () => {
        mlPublishService.preview.mockResolvedValue({
            data: { recommendations: [] },
        });
        mlPublishService.status.mockResolvedValue({
            data: { mlReachable: false, error: "timeout" },
        });

        render(<MlPublishPage />);
        fireEvent.click(screen.getByText("Запросить ML-превью"));

        await waitFor(() => expect(mlPublishService.preview).toHaveBeenCalled());
        await waitFor(() => expect(mlPublishService.status).toHaveBeenCalled());

        expect(screen.getByText(/ML service is unreachable/)).toBeInTheDocument();
    });

    it("shows index empty banner when status reports zero index size", async () => {
        mlPublishService.preview.mockResolvedValue({
            data: { recommendations: [] },
        });
        mlPublishService.status.mockResolvedValue({
            data: { mlReachable: true, indexSize: 0, config: { phashMaxDist: 12, grayBand: 4 } },
        });

        render(<MlPublishPage />);
        fireEvent.click(screen.getByText("Запросить ML-превью"));

        await waitFor(() => expect(mlPublishService.status).toHaveBeenCalled());
        expect(screen.getByText(/Training index is empty/)).toBeInTheDocument();
    });

    it("hides banners when preview has data", async () => {
        mlPublishService.preview.mockResolvedValue({
            data: {
                recommendations: [
                    {
                        id: "1",
                        url: "https://example.com/img1.jpg",
                        score: 0.8,
                        reason: "ok",
                        decision: "PUBLISH",
                        zone: "hit",
                        hash: "hash-1",
                    },
                ],
            },
        });

        render(<MlPublishPage />);
        fireEvent.click(screen.getByText("Запросить ML-превью"));

        await waitFor(() => expect(mlPublishService.preview).toHaveBeenCalled());
        expect(screen.queryByText(/ML service is unreachable/)).toBeNull();
        expect(screen.queryByText(/Training index is empty/)).toBeNull();
    });

    it("shows API key disabled info", async () => {
        mlPublishService.config.mockResolvedValue({
            data: {
                apiKeyConfigured: false,
                requireApiKey: false,
                maxBatchSize: 100,
                mlServiceConfig: { phashMaxDist: 12, grayBand: 4 },
            },
        });
        mlPublishService.preview.mockResolvedValue({
            data: { recommendations: [] },
        });
        mlPublishService.status.mockResolvedValue({
            data: { mlReachable: true, indexSize: 2, config: { phashMaxDist: 12, grayBand: 4 } },
        });

        render(<MlPublishPage />);

        await waitFor(() => expect(screen.getByText(/ML export auth is disabled/)).toBeInTheDocument());
    });
});
