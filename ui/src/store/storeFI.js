import {action, makeAutoObservable, observable} from "mobx";
import BackendApiService from "../service/BackendApiService";

class storeFI {
    //main
    @observable webImages = [];
    @observable step1 = false;
    @observable step2 = false;
    @observable selectedImages = [];

    constructor() {
        makeAutoObservable(this)
    }


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
            return response.data;
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
    async removeSelectedImageByIndex (index) {
        const reduceArr = [...this.selectedImages];
        reduceArr.splice(index, 1);
        this.selectedImages = reduceArr;
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