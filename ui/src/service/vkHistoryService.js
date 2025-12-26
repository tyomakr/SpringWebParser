import axios from "axios";

const API_ROOT = `${window.location.origin}/api/vk-history`;

const vkHistoryService = {
  entries() {
    return axios.get(`${API_ROOT}/entries`);
  },
  entriesPage(params) {
    return axios.get(`${API_ROOT}/entries/page`, { params });
  },
  training() {
    return axios.get(`${API_ROOT}/training`);
  },
  updateUseForTraining(id, useForTraining) {
    return axios.patch(`${API_ROOT}/entries/${id}/training`, { useForTraining });
  },
  syncWall(params) {
    return axios.post(`${API_ROOT}/sync-wall`, null, params ? { params } : undefined);
  },
  triggerSyncWall(params) {
    return axios.post(`${API_ROOT}/sync-wall/trigger`, null, params ? { params } : undefined);
  },
  syncStatus() {
    return axios.get(`${API_ROOT}/sync-wall/status`);
  },
};

export default vkHistoryService;
