import React, { useState } from "react";
import { Helmet } from "react-helmet/es/Helmet";
import { inject, observer } from "mobx-react";
import storeFI from "../../store/storeFI";
import { ImageList, ImageListItem, ImageListItemBar, IconButton } from "@mui/material";
import AddIcon from '@mui/icons-material/Add';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import Pagination from "../Pagination";
import Portal from "../Portal";

const Gallery = inject("storeFI")(observer((props) => {
    const [items] = useState(props.storeFI.webImages);
    const [pageOfItems, setPageOfItems] = useState([]);
    const [selectedImage, setSelectedImage] = useState(null); // Состояние для модалки

    const onChangePage = (pageOfItems) => {
        setPageOfItems(pageOfItems);
        window.scrollTo(0, 0);
    };

    const onHandleSelectImages = (webImage) => {
        storeFI.selectedImages.push(webImage);
    };

    return (
        <div>
            <Helmet
                htmlAttributes={{ "lang": "ru", "amp": undefined }}
                title="Галерея и выбор изображений"
                titleTemplate="Spring web parser - %s"
            />

            <div className="jumbotron">
                <h3 className="header-section">Просмотр и выбор изображений</h3>
                <div className="container-fluid">
                    <div className="text-center">
                        <ImageList cols={5} rowHeight="auto">
                            {pageOfItems.map((webImage, index) => (
                                <ImageListItem key={index}>
                                    <img
                                        className="img-gal"
                                        src={webImage.directLink}
                                        alt={webImage.id}
                                        loading="lazy"
                                    />
                                    <ImageListItemBar
                                        title={webImage.id}
                                        actionIcon={
                                            <div>
                                                <IconButton
                                                    sx={{ color: 'rgba(255, 255, 255, 0.54)' }}
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        setSelectedImage(webImage.directLink);
                                                    }}
                                                >
                                                    <ZoomInIcon />
                                                </IconButton>
                                                <IconButton
                                                    sx={{ color: 'rgba(255, 255, 255, 0.54)' }}
                                                    onClick={() => onHandleSelectImages(webImage)}
                                                >
                                                    <AddIcon />
                                                </IconButton>
                                            </div>
                                        }
                                    />
                                </ImageListItem>
                            ))}
                        </ImageList>
                        <Pagination items={items} onChangePage={onChangePage} />
                    </div>
                </div>
            </div>

            {/* Модальное окно для увеличения */}
            {selectedImage && (
                <Portal>
                    <div
                        style={{
                            position: 'fixed',
                            top: 0,
                            left: 0,
                            right: 0,
                            bottom: 0,
                            backgroundColor: 'rgba(0,0,0,0.7)',
                            zIndex: 1000000, // Больше, чем у header (999999)
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                        }}
                        onClick={() => setSelectedImage(null)}
                    >
                        <img
                            src={selectedImage}
                            style={{
                                maxWidth: '70vw',
                                maxHeight: '70vh',
                                objectFit: 'contain',
                                borderRadius: '8px',
                            }}
                            alt="Увеличенное изображение"
                        />
                    </div>
                </Portal>
            )}
        </div>
    );
}));

// Добавляем мемоизацию компонента для предотвращения ненужных перерисовок
const MemoizedGallery = React.memo(Gallery, (prevProps, nextProps) => {
    // Сравниваем пропсы, чтобы определить, нужно ли перерисовывать компонент
    return prevProps.storeFI.webImages === nextProps.storeFI.webImages &&
        prevProps.storeFI.selectedImages === nextProps.storeFI.selectedImages;
});

export default MemoizedGallery;