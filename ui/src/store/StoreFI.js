import { makeAutoObservable } from "mobx";
import backendApiService from "../service/backendApiService";

class StoreFI {
    webImages = [];
    selectedImages = [];
    currentStep = 0;

    constructor() {
        makeAutoObservable(this);
    }

    async getWebImagesFromPages(num1, num2) {
        try {
            this.webImages = [];
            const response = await backendApiService.getWebImagesOnPages(num1, num2);
            console.log(response.data);
            this.webImages = response.data;
            this.currentStep = 1;
        } catch (e) {
            console.error("Ошибка при получении изображений: ", e);
        }
    }

    async saveAndPublishSelectedImages(data) {
        try {
            const response = await backendApiService.saveAndPublishSelectedImages(data);
            this.currentStep = 2;
            return response.data;
        } catch (e) {
            console.error("Ошибка при публикации: ", e);
        }
    }

    clearStore() {
        this.webImages = [];
        this.selectedImages = [];
        this.resetSteps();
    }

    removeSelectedImageByIndex(index) {
        const reduceArr = [...this.selectedImages];
        reduceArr.splice(index, 1);
        this.selectedImages = reduceArr;
    }

    resetSteps() {
        this.currentStep = 0;
    }
}

const storeFI = new StoreFI();
export default storeFI;