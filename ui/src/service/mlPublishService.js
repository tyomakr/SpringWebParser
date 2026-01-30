import axios from "axios";

const PUBLISH_ROOT = `${window.location.origin}/api/ml/publish`;
const API_ROOT = `${window.location.origin}/api/ml`;

const publishClient = axios.create({
    baseURL: PUBLISH_ROOT,
    validateStatus: (status) => status >= 200 && status < 300,
});
const mlBaseClient = axios.create({
    baseURL: API_ROOT,
    validateStatus: (status) => status >= 200 && status < 300,
});
const mlPublishService = {
    preview(images) {
        return publishClient.post("/preview", { images });
    },
    commit(images) {
        return publishClient.post("/commit", { images });
    },
    config() {
        return mlBaseClient.get("/config");
    },
    status() {
        return mlBaseClient.get("/status");
    },
    ocrDiagnostics() {
        return mlBaseClient.get("/ocr-diagnostics");
    },
    runOcrDiagnostics(limit, offset) {
        const params = {};
        if (Number.isFinite(limit) && limit > 0) {
            params.limit = limit;
        }
        if (Number.isFinite(offset) && offset >= 0) {
            params.offset = offset;
        }
        return mlBaseClient.post("/ocr-diagnostics/run", null, { params });
    },
    feedback(entries) {
        return mlBaseClient.post("/feedback", entries);
    },
};

export default mlPublishService;
