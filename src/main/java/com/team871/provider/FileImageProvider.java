package com.team871.provider;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class FileImageProvider extends BufferedImageProvider {

    private final File file;

    public FileImageProvider(File file) throws IOException {
        super(ImageIO.read(file));
        this.file = file;
    }

    @Override
    public String getInfo(){
        return file.getName();
    }


}
