import { makeAutoObservable, runInAction } from 'mobx';
import backendApiService from '../service/backendApiService';
import { toast } from 'react-toastify';

/**
 * Store для шагов Stepper
 */
export class StoreFI {
    /** Список загруженных с веба изображений (шаг 1) */
    webImages = [];

    /** Флаг успешного завершения первого шага */
    step1 = false;

    /** Список выбранных на шаге 2 изображений */
    selectedImages = [];

    /** Текущая страница (шаг 3) */
    page = 1;

    /** Размер страницы (шаг 3) */
    pageSize = 40;

    constructor() {
        makeAutoObservable(this);
    }

    /**
     * Шаг 1: загрузка картинок по диапазону страниц.
     */
    async getWebImagesFromPages(fromPage, toPage) {
        this.step1 = false;
        this.webImages = [];

        try {
            const response = await backendApiService.getWebImagesOnPages(fromPage, toPage);
            runInAction(() => {
                this.webImages = response.data;
                this.step1 = true;
            });
        } catch (e) {
            console.error('Ошибка при запросе изображений:', e);
            toast.error(`Не удалось загрузить изображения: ${e.message}`, {
                position: 'bottom-right'
            });
        }
    }

    /**
     * Шаг 2: выбор / снятие выбора изображения.
     */
    toggleSelectImage(image) {
        const idx = this.selectedImages.findIndex(x => x.directLink === image.directLink);
        if (idx === -1) {
            this.selectedImages.push(image);
        } else {
            this.selectedImages.splice(idx, 1);
        }
    }

    /**
     * Шаг 2: удаление по индексу (для кнопки «корзина» на шаге 3).
     *
     * @param {number} index — индекс изображения в selectedImages
     */
    removeSelectedImageByIndex(index) {
        console.log('[StoreFI] Удаление изображения по индексу:', index);
        if (index >= 0 && index < this.selectedImages.length) {
            // Надёжный способ мутировать observable-массив
            this.selectedImages = [
                ...this.selectedImages.slice(0, index),
                ...this.selectedImages.slice(index + 1)
            ];
            console.log('[StoreFI] После удаления, selectedImages:', this.selectedImages.length);
        } else {
            console.warn('[StoreFI] Индекс вне диапазона:', index);
        }
    }

    /**
     * Шаг 3: переставить два элемента местами.
     */
    reorderSelectedImages(fromIndex, toIndex) {
        const arr = this.selectedImages.slice();
        const [moved] = arr.splice(fromIndex, 1);
        arr.splice(toIndex, 0, moved);
        this.selectedImages = arr;
        console.log('[StoreFI] reorderSelectedImages', fromIndex, toIndex);
    }

    /**
     * Шаг 3: отправка на сервер, с удалением дубликатов по directLink.
     */
    async saveAndPublishSelectedImages(images) {
        const unique = images.filter(
            (img, idx, arr) =>
                arr.findIndex(x => x.directLink === img.directLink) === idx
        );
        return backendApiService.saveAndPublishSelectedImages(unique);
    }

    /**
     * Полностью сбросить стор (для кнопки «Сбросить»).
     */
    clearStore() {
        this.webImages = [];
        this.step1 = false;
        this.selectedImages = [];
        this.page = 1;
    }
}

// Экспортируем экземпляр store
const storeFI = new StoreFI();
export default storeFI;
