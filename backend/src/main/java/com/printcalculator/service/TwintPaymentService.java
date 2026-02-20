package com.printcalculator.service;

import io.nayuki.qrcodegen.QrCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

@Service
public class TwintPaymentService {

    private final String twintPaymentUrl;

    public TwintPaymentService(
            @Value("${payment.twint.url:https://go.twint.ch/1/e/tw?tw=acq.gERQQytOTnyIMuQHUqn4hlxgciHE5X7nnqHnNSPAr2OF2K3uBlXJDr2n9JU3sgxa.}")
            String twintPaymentUrl
    ) {
        this.twintPaymentUrl = twintPaymentUrl;
    }

    public String getTwintPaymentUrl() {
        return twintPaymentUrl;
    }

    public byte[] generateQrPng(int sizePx) {
        try {
            // Use High Error Correction for financial QR codes
            QrCode qrCode = QrCode.encodeText(twintPaymentUrl, QrCode.Ecc.HIGH);
            
            // Standard QR quiet zone is 4 modules
            int borderModules = 4;
            int fullModules = qrCode.size + borderModules * 2;
            int scale = Math.max(1, sizePx / fullModules);
            int imageSize = fullModules * scale;

            BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, imageSize, imageSize);
                graphics.setColor(Color.BLACK);

                for (int y = 0; y < qrCode.size; y++) {
                    for (int x = 0; x < qrCode.size; x++) {
                        if (qrCode.getModule(x, y)) {
                            int px = (x + borderModules) * scale;
                            int py = (y + borderModules) * scale;
                            graphics.fillRect(px, py, scale, scale);
                        }
                    }
                }
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate TWINT QR image.", ex);
        }
    }
}
