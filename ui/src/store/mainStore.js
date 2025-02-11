import StoreFI from "./storeFI"
import ThemeStore from "./themeStore"

export default class mainStore {

    constructor() {
        /* init child stores */
        this.storeFI = new StoreFI();
        this.themeStore = new ThemeStore();
    }
}
