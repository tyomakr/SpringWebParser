import React, {useState} from "react";
import {Helmet} from "react-helmet/es/Helmet";
import Pagination from "../Pagination";
import {inject, observer} from "mobx-react";
import storeFI from "../../store/storeFI";
import {ImageList} from "@mui/material";
import {ImageListItem} from "@mui/material";
import {ImageListItemBar} from "@mui/material";
import {IconButton} from "@mui/material";
import AddIcon from '@mui/icons-material/Add';


const Gallery = inject("storeFI")(observer((props) => {

    const [items] = useState(props.storeFI.webImages);

    const [pageOfItems, setPageOfItems] = useState([]);

    const onChangePage = (pageOfItems) => {setPageOfItems(pageOfItems);}

    const onHandleSelectImages = (webImage) => {
        storeFI.selectedImages.push(webImage);
        console.log("selected: " + storeFI.selectedImages.length);
    }

    return(
        <div>
            <Helmet
                htmlAttributes={{"lang": "ru", "amp": undefined}}
                title="Галерея и выбор изображений"
                titleTemplate="Spring web parser - %s"/>

            <div className="jumbotron">
                <h3 className="header-section">Просмотр и выбор изображений</h3>
                <div className="container-fluid">
                    <div className="text-center">
                        <ImageList cols={5} rowHeight='auto'>
                            {pageOfItems.map((webImage, index) => (
                                <ImageListItem key={index}
                                    // sx={{width: 300, height: 300}}
                                >
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
                                                        onClick={() => {
                                                            onHandleSelectImages(webImage);
                                                        }}>
                                                <AddIcon/>
                                            </IconButton>
                                        }
                                    />
                                </ImageListItem>
                            ))}
                        </ImageList>
                        <Pagination items={items} onChangePage={onChangePage}/>
                    </div>
                </div>
            </div>
        </div>
    );
}));

export default Gallery;
