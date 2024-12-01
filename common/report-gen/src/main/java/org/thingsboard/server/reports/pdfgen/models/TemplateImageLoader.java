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
package org.thingsboard.server.reports.pdfgen.models;

import com.lowagie.text.Image;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.xhtmlrenderer.pdf.ITextFSImage;

import java.io.IOException;
import java.io.InputStream;

public enum TemplateImageLoader {

    COMPANY_LOGO(resolveImageElement("company_logo.jpg"));

    private final ITextFSImage texImageElement;

    TemplateImageLoader(final ITextFSImage texImageElement) {
        this.texImageElement = texImageElement;
    }

    public ITextFSImage getValue() {
        return this.texImageElement;
    }

    private static ITextFSImage resolveImageElement(final String imagePath) {
        try (final InputStream is = new ClassPathResource("pdf_templates/images/" + imagePath).getInputStream()) {
            final Image image = Image.getInstance(IOUtils.toByteArray(is));
            return new ITextFSImage(image);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
