import {observable, action} from "mobx";
import BackendApiService from "../service/BackendApiService";

export default class storeFI {
    @observable webImages = [];
    @observable currentImage = null;


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
}