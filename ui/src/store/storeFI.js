import {makeAutoObservable} from "mobx";
import backendApiService from "../service/backendApiService";


class storeFI {

    webImages = [];
    step1 = false;
    step2 = false;
    selectedImages = [];

    constructor() {
        makeAutoObservable(this)
    }

    async getWebImagesFromPages(num1, num2) {
        try {
            this.webImages = [];
            const response = await backendApiService.getWebImagesOnPages(num1, num2);
            console.log(response.data);
            this.webImages = response.data;
        } catch (e) {
            console.log("err on GetWebImagesFromPages method: " + e)
        }
    }

    async saveAndPublishSelectedImages(data) {
        try {
            const response = await backendApiService.saveAndPublishSelectedImages(data);
            return response.data;
        } catch (e) {
            console.log("err on saveAndPublishSelectedImages method: " + e)
        }
    }

    async clearStore() {
        try {
            this.webImages = [];
            this.selectedImages = [];
            await this.resetStepsState()
        } catch (e) {
            console.log("err on clearStore method: " + e)
        }
    }


    async removeSelectedImageByIndex (index) {
        const reduceArr = [...this.selectedImages];
        reduceArr.splice(index, 1);
        this.selectedImages = reduceArr;
    }


    async resetStepsState() {
        try {
            this.step1 = false;
            this.step2 = false;
        } catch (e) {
            console.log("err on resetStepsState method: " + e)
        }
    }
}

// eslint-disable-next-line
export default new storeFI();