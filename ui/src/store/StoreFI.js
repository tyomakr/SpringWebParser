
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
        // Автоматически сделаем все поля и методы наблюдаемыми / action
        makeAutoObservable(this);
    }

    /**
     * Шаг 1: загрузка картинок по диапазону страниц.
     * В случае ошибки показываем toast.error и оставляем step1=false.
     *
     * @param {number} fromPage — первая страница
     * @param {number} toPage   — последняя страница
     */
    async getWebImagesFromPages(fromPage, toPage) {
        // сброс состояния
        this.step1 = false;
        this.webImages = [];

        try {
            // HTTP-запрос
            const response = await backendApiService.getWebImagesOnPages(fromPage, toPage);

            // обновляем состояние в действии
            runInAction(() => {
                this.webImages = response.data;
                this.step1 = true;
            });
        } catch (e) {
            // логируем в консоль
            console.error('Ошибка при запросе изображений:', e);
            // уведомляем пользователя
            toast.error(`Не удалось загрузить изображения: ${e.message}`, {
                position: 'bottom-right'
            });
            // step1 остаётся false
        }
    }

    /**
     * Шаг 2: выбор / снятие выбора изображения.
     * Если изображения нет в списке, добавляем, иначе удаляем.
     *
     * @param {object} image — объект WebImage (с directLink и пр.)
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
        if (index >= 0 && index < this.selectedImages.length) {
            this.selectedImages.splice(index, 1);
        }
    }

    /**
     * Шаг 3: переставить два элемента местами.
     *
     * @param {number} fromIndex — исходный индекс
     * @param {number} toIndex   — новый индекс
     */
    reorderSelectedImages(fromIndex, toIndex) {
        const arr = this.selectedImages;
        const [moved] = arr.splice(fromIndex, 1);
        arr.splice(toIndex, 0, moved);
    }

    /**
     * Шаг 3: отправка на сервер, с удалением дубликатов по directLink.
     *
     * @param {Array} images — массив объектов с полем directLink
     * @returns {Promise<string>} — ответ от API
     */
    async saveAndPublishSelectedImages(images) {
        // убираем дубли по directLink
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

// присваиваем экземпляр переменной, чтобы не было eslint-варнинга про анонимный default export
const storeFI = new StoreFI();

export default storeFI;