import {observable, action} from "mobx";
import BackendApiService from "../service/BackendApiService";

class storeFI {
    @observable webImages = [];
    @observable currentImage = null;
    @observable selectedWebImages = [];


    @action
    async getWebImagesFromPages(num1, num2) {
        try {
            this.webImages = [];
            const response = await BackendApiService.getWebImagesOnPages(num1, num2);
            console.log(response.data);
            this.webImages = response.data;
        } catch (e) {
            console.log(e)
        }
    }

    @action
    async clearStore() {
        try {
            this.webImages = [];
        } catch (e) {
            console.log(e)
        }
    }
}


export default new storeFI();