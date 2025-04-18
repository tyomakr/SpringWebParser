import {makeAutoObservable} from "mobx";

const themes = {
    light: 'light',
    dark: 'dark',
};

class ThemeStore {
    mode = themes.light;

    constructor() {
        makeAutoObservable(this);
    }

    toggleMode = () => {
        if (this.mode === themes.dark) {
            this.mode = themes.light;
        } else {
            this.mode = themes.dark;
        }
        return this;
    };
}

const themeStoreInstance = new ThemeStore();
export default themeStoreInstance;