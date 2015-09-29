/*
 * Copyright 2015 ROLLUS Loïc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrieval.testvector.generator.exception;

import retrieval.exception.CBIRException;

/**
 * Argument for tests vectors creation error (ex: negative value for m)
 * @author Rollus Loic
 */
public class TestsVectorsArgumentException extends CBIRException {

    /**
     * Error Code
     */
    public static final String CODE = "5001";

    /**
     * Creates a new instance of <code>AlreadyInPuctureIndexException</code> without detail message.
     */
    public TestsVectorsArgumentException() {
        super(CODE,"");
    }


    /**
     * Constructs an instance of <code>AlreadyInPuctureIndexException</code> with the specified detail message.
     * @param msg the detail message.
     */
    public TestsVectorsArgumentException(String msg) {
        super(CODE,msg);
    }
}