/*
 *  Copyright (C) 2009-2013 VMware, Inc. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.wavemaker.runtime.server.json.converters;

import java.io.IOException;
import java.io.Writer;
import java.sql.Blob;
import java.sql.SQLException;

import org.apache.commons.io.IOUtils;
import org.hibernate.lob.BlobImpl;

import com.wavemaker.common.MessageResource;
import com.wavemaker.common.WMRuntimeException;
import com.wavemaker.json.JSONArray;
import com.wavemaker.json.JSONMarshaller;
import com.wavemaker.json.type.converters.ReadObjectConverter;
import com.wavemaker.json.type.converters.WriteObjectConverter;
import com.wavemaker.json.type.reflect.PrimitiveReflectTypeDefinition;
import com.wavemaker.json.type.reflect.ReflectTypeUtils;

/**
 * TypeDefinition for types extending from {@link Blob}.
 * 
 * @author Matt Small
 */
public class BlobTypeDefinition extends PrimitiveReflectTypeDefinition implements ReadObjectConverter, WriteObjectConverter {

    public BlobTypeDefinition(Class<? extends Blob> klass) {

        super();
        this.setKlass(klass);
        this.setTypeName(ReflectTypeUtils.getTypeName(this.getKlass()));
    }

    @Override
    public Object readObject(Object input, Object root, String path) {

        if (input == null) {
            return null;
        } else if (JSONArray.class.isAssignableFrom(input.getClass())) {
            JSONArray jarr = (JSONArray) input;
            byte[] barr = new byte[jarr.size()];

            for (int i = 0; i < barr.length; i++) {
                if (Number.class.isAssignableFrom(jarr.get(i).getClass())) {
                    barr[i] = ((Number) jarr.get(i)).byteValue();
                } else {
                    throw new WMRuntimeException("expected number, not: " + jarr.get(i) + " (class: " + jarr.get(i).getClass() + ")");
                }
            }

            return new BlobImpl(barr);
        } else {
            return input;
        }
    }

    @Override
    public void writeObject(Object input, Object root, String path, Writer writer) throws IOException {

        if (input == null) {
            JSONMarshaller.marshal(writer, input);
        } else if (input instanceof Blob) {
            try {
                byte[] bytes = IOUtils.toByteArray(((Blob) input).getBinaryStream());

                JSONMarshaller.marshal(writer, bytes);
            } catch (SQLException e) {
                throw new WMRuntimeException(e);
            }
        } else {
            throw new WMRuntimeException(MessageResource.JSON_UNHANDLED_TYPE, input, input.getClass());
        }
    }
}