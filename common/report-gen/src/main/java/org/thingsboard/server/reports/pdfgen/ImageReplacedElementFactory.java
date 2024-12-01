/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.reports.pdfgen;

import com.lowagie.text.Image;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.thingsboard.server.reports.pdfgen.models.TemplateImageLoader;
import org.thymeleaf.util.StringUtils;
import org.w3c.dom.Element;
import org.xhtmlrenderer.extend.ReplacedElement;
import org.xhtmlrenderer.extend.ReplacedElementFactory;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.pdf.ITextFSImage;
import org.xhtmlrenderer.pdf.ITextImageElement;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.simple.extend.FormSubmissionListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
public class ImageReplacedElementFactory implements ReplacedElementFactory {
    private final ReplacedElementFactory superFactory;

    public ImageReplacedElementFactory(final ReplacedElementFactory superFactory) {
        this.superFactory = superFactory;
    }

    @Override
    public ReplacedElement createReplacedElement(final LayoutContext c, final BlockBox box, final UserAgentCallback uac,
                                                 int cssWidth, int cssHeight) {
        final Element element = box.getElement();
        if (Objects.nonNull(element) && element.getNodeName().equals("img")) {
            try {
                ITextFSImage textFSImage = getiTextFSImage(element);
                if (Objects.nonNull(textFSImage)) {
                    textFSImage.scale(cssWidth, cssHeight);
                    return new ITextImageElement(textFSImage);
                }
            } catch (Exception ex) {
                log.warn("Exception occurred while replacing images in template. There is a Possibility of distortion " +
                        "of generated pdf", ex);
            }
        }
        return null;
    }

    private ITextFSImage getiTextFSImage(Element element) {
        if (!StringUtils.isEmptyOrWhitespace(element.getAttribute("src"))) {
            return generateImageFromSrc(element);
        }

        String className = element.getAttribute("class");
        return switch (className) {
            case "company_logo" -> TemplateImageLoader.COMPANY_LOGO.getValue();
            default -> null;
        };
    }

    private ITextFSImage generateImageFromSrc(final Element element) {
        try {
            final Image image = Image.getInstance(Base64.decodeBase64(element.getAttribute("src")
                    .getBytes(StandardCharsets.UTF_8)));
            return new ITextFSImage(image);
        } catch (IOException e) {
            log.error("Exception occurred by decoding base64 image in src.  Probably not a right base64 string", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reset() {
        superFactory.reset();
    }

    @Override
    public void remove(Element e) {
        superFactory.remove(e);
    }

    @Override
    public void setFormSubmissionListener(FormSubmissionListener listener) {
        superFactory.setFormSubmissionListener(listener);
    }
}
