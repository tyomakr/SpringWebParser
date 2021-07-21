import {observable, action} from "mobx";
import BackendApiService from "../service/BackendApiService";
import {act} from "react-dom/test-utils";

class storeFI {
    //main
    @observable webImages = [];
    @observable step1 = false;
    @observable step2 = false;
    @observable selectedImages = [];


    @action
    async getWebImagesFromPages(num1, num2) {
        try {
            this.webImages = [];
            const response = await BackendApiService.getWebImagesOnPages(num1, num2);
            console.log(response.data);
            this.webImages = response.data;
        } catch (e) {
            console.log("err on GetWebImagesFromPages method: " + e)
        }
    }

    @action
    async saveAndPublishSelectedImages(data) {
        try {
            const response = await BackendApiService.saveAndPublishSelectedImages(data);
            return response.status;
        } catch (e) {
            console.log("err on saveAndPublishSelectedImages method: " + e)
        }
    }

    @action
    async clearStore() {
        try {
            this.webImages = [];
            this.selectedImages = [];
        } catch (e) {
            console.log("err on clearStore method: " + e)
        }
    }

    @action
    async resetStepsState() {
        try {
            this.step1 = false;
            this.step2 = false;
        } catch (e) {
            console.log("err on resetStepsState method: " + e)
        }
    }


}


export default new storeFI();