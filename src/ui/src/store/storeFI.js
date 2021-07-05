import {observable, action} from "mobx";
import { Map } from 'immutable'
import BackendApiService from "../service/BackendApiService";

class storeFI {
    //main
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

    @action
    async clearStore() {
        try {
            this.webImages = [];
        } catch (e) {
            console.log(e)
        }
    }


    //picker
    @observable newerPickedImage = [];
    @observable pickedImageToArray = [];
    @observable picked = Map();

    @action
    async clearPickerStore() {
        try {
            this.pickedImageToArray = [];
            this.newerPickedImage = [];
            this.picked.removeAll();
        } catch (e) {
            console.log(e)
        }
    }


}


export default new storeFI();