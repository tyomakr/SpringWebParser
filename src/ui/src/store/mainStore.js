import StoreFI from "./storeFI";

export default class mainStore {
    constructor() {
        /* init child stores */
        this._storeFI = new StoreFI();
    }

    get storeFI() {
        return this._storeFI;
    }
}