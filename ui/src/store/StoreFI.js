import { makeAutoObservable } from 'mobx';
import backendApiService from '../service/backendApiService';

class StoreFI {
    webImages = [];
    step1 = false;
    selectedImages = [];

    constructor() {
        makeAutoObservable(this);
    }

    /**
     * Шаг 1: запрос картинок.
     * После каждого нового запроса сбрасываем флаг step1,
     * а по окончании — устанавливаем его в true.
     */
    async getWebImagesFromPages(fromPage, toPage) {
        this.step1 = false;
        this.webImages = [];
        try {
            const response = await backendApiService.getWebImagesOnPages(fromPage, toPage);
            this.webImages = response.data;
            this.step1 = true;
        } catch (e) {
            console.error('Ошибка при запросе изображений:', e);
            // step1 останется false
        }
    }

    /**
     * Шаг 3: отправка выбранных изображений.
     */
    async saveAndPublishSelectedImages(images) {
        try {
            const result = await backendApiService.saveAndPublishSelectedImages(images);
            return result;
        } catch (e) {
            console.error('Ошибка при отправке выбранных изображений:', e);
            throw e;
        }
    }

    /**
     * Удаление изображения из выбранных по индексу.
     */
    removeSelectedImageByIndex(index) {
        this.selectedImages.splice(index, 1);
    }

    /**
     * Полная очистка стейта (для кнопки «Сброс»).
     */
    clearStore() {
        this.webImages = [];
        this.selectedImages = [];
        this.step1 = false;
    }

    clearWebImages() {
        this.webImages = [];
        this.step1 = false;
    }
}

const StoreFIInstance = new StoreFI();
export default StoreFIInstance;