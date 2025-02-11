import React from "react";
import {Helmet} from "react-helmet/es/Helmet";
import {inject, observer} from "mobx-react";
import storeFI from "../../store/storeFI";
import {toast} from "react-toastify";
import {IconButton, ImageList, ImageListItem, ImageListItemBar} from "@mui/material";
import DeleteOutlined from "@mui/icons-material/Delete";

const PrepareToSendImages = inject("storeFI")(observer((props) => {

    const onHandleRemoveImageByIndex = (index) => {
        storeFI.removeSelectedImageByIndex(index);
    }

    const onHandleSendImages = () => {
        if (storeFI.selectedImages.length > 0) {

            storeFI.saveAndPublishSelectedImages(storeFI.selectedImages).then(res =>
                toast.success(
                    res, {
                        position: "bottom-right",
                        autoClose: 15000,
                        hideProgressBar: true,
                        closeOnClick: true,
                        pauseOnHover: true,
                        draggable: true,
                        progress: undefined,
                    })
            )
        }
    }

    return (
        <div>
            <Helmet
                htmlAttributes={{"lang": "ru", "amp": undefined}}
                title="Подготовка к отправке..."
                titleTemplate="Spring web parser - %s" />

            <div className="jumbotron">
                <h4 className="header-section">Подготовка к отправке</h4>

                <div className="container-fluid">
                    <div className="text-center">
                        <ImageList cols={10} rowHeight='auto'>
                            {storeFI.selectedImages.map((webImage, index) => (
                                <ImageListItem key={index}>
                                    <img
                                        className={"img-gal"}
                                        src={webImage.directLink}
                                        alt={webImage.id}
                                        loading="lazy"
                                    />
                                    <ImageListItemBar
                                        title={webImage.id}
                                        actionIcon={
                                            <IconButton type="button"
                                                        title={webImage.id}
                                                        sx={{color: 'rgba(255, 255, 255, 0.54)'}}
                                                        onClick={() => onHandleRemoveImageByIndex(index)}>
                                                <DeleteOutlined/>
                                            </IconButton>
                                        }
                                    />
                                </ImageListItem>
                            ))}
                        </ImageList>
                    </div>
                    <div className="text-center">
                        <span>Дубликаты изображений дополнительно отсекаются на стороне бэкэнда</span>
                    </div>
                </div>

                <div className="container-fluid separator-margin">
                    <button className="btn btn-outline-success" type="button"
                            onClick={()=> onHandleSendImages()}>ОТПРАВИТЬ</button>
                </div>
            </div>
        </div>
    );
}));

export default PrepareToSendImages;