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

import com.lowagie.text.DocumentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;
import org.thingsboard.server.reports.pdfgen.context.PdfGenerationContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.TemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public abstract class AbstractPdfGenerator implements IPdfGeneratorService {

    private final String template;
    private final String localStoragePath;
    private final TemplateEngine templateEngine;

    protected AbstractPdfGenerator(final String template,
                                   final TemplateEngine templateEngine,
                                   final String localStoragePath) {
        this.template = template;
        this.templateEngine = templateEngine;
        this.localStoragePath = localStoragePath;
    }

    @Override
    public byte[] generatePdf(final String requestId, final PdfGenerationContext context) {
        return generatePdf(requestId, context.asContext());
    }

    protected byte[] generatePdf(final String requestId, final Context context) {
        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        final String html = templateEngine.process(this.template, context);
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final ITextRenderer renderer = new ITextRenderer();
            renderer.getSharedContext().setPrint(true);
            renderer.getSharedContext().setInteractive(false);
            renderer.getSharedContext().setReplacedElementFactory(new ImageReplacedElementFactory(
                    renderer.getSharedContext().getReplacedElementFactory()));
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);
            renderer.finishPDF();
            log.info("[{}] Successfully generated PDF.", requestId);
            final byte[] output = outputStream.toByteArray();
            savePdfToLocalStorage(requestId, output);
            log.info("[{}] Successfully saved PDF to {}.", requestId, localStoragePath);
            return output;
        } catch (final DocumentException | IOException e) {
            log.error("[{}] Error while generating PDF. error: {}", requestId, e.getMessage(), e);
            throw new RuntimeException("Error while generating PDF");
        } finally {
            log.info("[{}] PDF generated in {} milliseconds.", requestId, stopWatch.getTotalTimeMillis());
        }
    }

    private void savePdfToLocalStorage(final String requestId, final byte[] pdfBytes) {
        // Specify the path to the local folder where you want to store the PDF
        Path outputPath = Paths.get(localStoragePath, requestId + ".pdf");

        try {
            if (!Files.exists(outputPath.getParent())) {
                Files.createDirectories(outputPath.getParent());
            }
            // Write the PDF bytes to the file
            Files.write(outputPath, pdfBytes);
        } catch (final IOException e) {
            log.error("[{}] Error while saving PDF to local path - {}. error: {}",
                    requestId, localStoragePath, e.getMessage(), e);
        }
    }
}
