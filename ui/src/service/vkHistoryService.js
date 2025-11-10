import axios from "axios";

const API_ROOT = `${window.location.origin}/api/vk-history`;

const vkHistoryService = {
  entries() {
    return axios.get(`${API_ROOT}/entries`);
  },
  training() {
    return axios.get(`${API_ROOT}/training`);
  },
  updateUseForTraining(id, useForTraining) {
    return axios.patch(`${API_ROOT}/entries/${id}/training`, { useForTraining });
  },
};

export default vkHistoryService;
