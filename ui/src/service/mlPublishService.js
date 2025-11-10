import axios from "axios";

const API_BASE_URL = process.env.REACT_APP_BACKEND_URL || window.location.origin;

const mlClient = axios.create({
    baseURL: `${API_BASE_URL}/api/ml/publish`,
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
