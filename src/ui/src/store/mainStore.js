import StoreFI from "./storeFI";

export default class mainStore {
    constructor() {
        /* init child stores */
        this.storeFI = new StoreFI();
    }
}