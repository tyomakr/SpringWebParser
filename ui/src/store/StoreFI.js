import { makeAutoObservable } from 'mobx';
import backendApiService from '../service/backendApiService';

class StoreFI {
    // Список всех полученных изображений (Шаг 1)
    webImages = [];
    // Флаг окончания шага 1
    step1 = false;
    // Пользовательский выбор для отправки (Шаг 2 и 3)
    selectedImages = [];
    // Текущая страница в галерее отправки
    page = 1;
    // Количество элементов на странице
    pageSize = 40;

    constructor() {
        makeAutoObservable(this);
    }

    /**
     * Шаг 1: запрос картинок.
     * После каждого нового запроса сбрасываем флаг step1,
     * а по окончании — устанавливаем его в true.
     *
     * @param {number} fromPage — первая страница
     * @param {number} toPage — последняя страница
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
     * Шаг 3: отправка выбранных изображений.
     *
     * @param {Array<{url: string}>} images — массив объектов с URL
     * @returns {Promise<string>} — результат отправки
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
     *
     * @param {number} index — индекс в массиве selectedImages
     */
    removeSelectedImageByIndex(index) {
        this.selectedImages.splice(index, 1);
    }

    /**
     * Позволяет переставить изображения местами,
     * сохраняя порядок, который задал пользователь в UI.
     *
     * @param {number} fromIndex — исходная позиция
     * @param {number} toIndex — целевая позиция
     */
    reorderSelectedImages(fromIndex, toIndex) {
        const items = Array.from(this.selectedImages);
        const [moved] = items.splice(fromIndex, 1);
        items.splice(toIndex, 0, moved);
        this.selectedImages = items;
    }

    /**
     * Полная очистка стейта (для кнопки «Сброс»).
     */
    clearStore() {
        this.webImages = [];
        this.selectedImages = [];
        this.step1 = false;
    }

    /**
     * Очистка только webImages и сброс флага step1.
     */
    clearWebImages() {
        this.webImages = [];
        this.step1 = false;
    }
}

// Экземпляр по умолчанию для приложения
const StoreFIInstance = new StoreFI();
export default StoreFIInstance;
// Именованный экспорт класса для unit-тестов
export { StoreFI };