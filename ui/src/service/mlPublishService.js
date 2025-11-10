import axios from "axios";

const API_ROOT = `${window.location.origin}/api/ml/publish`;

const mlClient = axios.create({
    baseURL: API_ROOT,
    validateStatus: (status) => status >= 200 && status < 300,
});
const mlPublishService = {
    preview(images) {
        return mlClient.post("/preview", { images });
    },
    commit(images) {
        return mlClient.post("/commit", { images });
    },
};

export default mlPublishService;
