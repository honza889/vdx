/*
 * Copyright 2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.projectodd.vdx.core;

import javax.xml.stream.XMLStreamException;

public class XMLStreamValidationException extends XMLStreamException {
    public XMLStreamValidationException(final String message,
                                        final ValidationError validationError,
                                        final Throwable nested) {
        super(message, validationError.location(), nested);
        this.validationError = validationError;
    }

    public ValidationError getValidationError() {
        return validationError;
    }

    private final ValidationError validationError;
}
