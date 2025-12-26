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
            toast.success(`Загружено ${response.data.length} изображений`, { position: 'bottom-right' });
        } catch (e) {
            console.error('Ошибка при запросе изображений:', e);
            const serverMessage = e.response?.data ?? e.message;
            toast.error(`Не удалось загрузить изображения: ${serverMessage}`, {
                position: 'bottom-right'
            });
            this.step1 = false;
            this.webImages = [];
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
        try {
            console.log('[StoreFI] Sending', unique.length, 'images to backend');
            const response = await backendApiService.saveAndPublishSelectedImages(unique);
            console.log('[StoreFI] Response received:', response.status, response.data);
            // Возвращаем данные из ответа axios (response.data содержит строку от сервера)
            return response.data;
        } catch (error) {
            console.error('[StoreFI] Error in saveAndPublishSelectedImages:', error);
            // Если axios выбросил ошибку, но есть ответ от сервера (даже с кодом ошибки), используем его
            if (error.response?.data) {
                console.log('[StoreFI] Server returned error response:', error.response.status, error.response.data);
                const serverData = error.response.data;
                const fieldErrors = serverData.fieldErrors
                    ? Object.values(serverData.fieldErrors).flat().join('; ')
                    : undefined;
                const msg = fieldErrors || serverData.error || serverData.message || error.message;
                // Если статус 2xx — воспринимаем как успех
                if (error.response.status >= 200 && error.response.status < 300) {
                    return serverData;
                }
                const serverError = new Error(msg);
                serverError.response = error.response;
                throw serverError;
            }
            throw error;
        }
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
