/*
 * Copyright (C) 2017. The UAPI Authors
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at the LICENSE file.
 *
 * You must gained the permission from the authors if you want to
 * use the project into a commercial product
 */

package uapi.exception;

import uapi.GeneralException;
import uapi.InvalidArgumentException;
import uapi.UapiException;
import uapi.common.ArgumentChecker;
import uapi.common.Builder;
import uapi.common.Guarder;
import uapi.common.StringHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The exception add ability to define error message which is constructed by various parameters.
 *
 * Every exception extend from ParameterizedException will provide two parameters: error code and category.
 *
 * The category is a integer value, 0x0000 ~ 0xFFFF is reserved by framework.
 * The projects which are in the uapi.base repo will reserve category id from 0x0000 ~ 0x00FF
 * The projects which are in the uapi.cornerstone will reserve category id from 0x0100 ~ 0x01FF
 */
public abstract class ParameterizedException extends UapiException {

    private static final Lock CATEGORY_REG_LOCK                                             = new ReentrantLock();
    private static final Map<Integer, Class<? extends ParameterizedException>> CATEGORY_REG = new HashMap<>();

    /**
     * Check is the exception category registered by other exception or not.
     *
     * @param   category
     *          The exception category
     * @param   exceptionClass
     *          The exception class
     * @throws  GeneralException
     *          If the exception category is registered by other exception class
     */
    private static void checkCategory(
            final int category,
            final Class<? extends ParameterizedException> exceptionClass
    ) throws GeneralException {
        ArgumentChecker.required(exceptionClass, "exceptionClass");
        Guarder.by(CATEGORY_REG_LOCK).run(() -> {
            Class<? extends ParameterizedException> regExClass = CATEGORY_REG.get(category);
            if (regExClass == null) {
                CATEGORY_REG.put(category, exceptionClass);
            } else if (! regExClass.equals(exceptionClass)) {
                if (! exceptionClass.isAssignableFrom(regExClass) && ! regExClass.isAssignableFrom(exceptionClass)) {
                    throw new GeneralException("The category [{}] is registered by exception - {}", category, regExClass);
                }
                // Using super exception class to register
                if (regExClass.isAssignableFrom(exceptionClass)) {
                    CATEGORY_REG.put(category, exceptionClass);
                }
            }
        });
    }

    private final ExceptionBuilder _builder;

    protected ParameterizedException(final ExceptionBuilder builder) {
        super();
        checkCategory(builder._category, this.getClass());
        this._builder = builder;
    }

    public int errorCode() {
        return this._builder._errCode;
    }

    public int category() {
        return this._builder._category;
    }

    @Override
    public String getMessage() {
        String msgTemp = this._builder._errors.getMessageTemplate(this);
        if (msgTemp == null) {
            return super.getMessage();
        } else {
            return StringHelper.makeString(msgTemp, this._builder._namedVars, this._builder._indexedVars);
        }
    }

    public static abstract class ExceptionBuilder<E extends ParameterizedException, B extends ExceptionBuilder>
            extends Builder<E> {

        private int _errCode = -1;
        private int _category = -1;
        private final ExceptionErrors<E> _errors;
        private Object[] _indexedVars;
        private Map _namedVars;

        public ExceptionBuilder(final int category, final ExceptionErrors<E> errors) {
            if (category < 0) {
                throw new GeneralException("The exception category cant be negative");
            }
            if (errors == null) {
                throw new GeneralException("The ExceptionErrors is not specified");
            }
            this._category = category;
            this._errors = errors;
        }

        public B errorCode(int errorCode) {
            this._errCode = errorCode;
            return (B) this;
        }

        public B variables(Object... vars) {
            this._indexedVars = vars;
            return (B) this;
        }

        public B variables(Map vars) {
            this._namedVars = vars;
            return (B) this;
        }

        public B variables(IParameters vars) {
            Object v = vars.get();
            if (v instanceof Object[]) {
                this._indexedVars = (Object[]) v;
            } else if (v instanceof Map) {
                this._namedVars = (Map) v;
            } else {
                throw new GeneralException("Unsupported variables type - {}", v.getClass().getCanonicalName());
            }
            return (B) this;
        }

        @Override
        protected void validate() throws InvalidArgumentException {
            if (this._category == -1) {
                throw new InvalidArgumentException("The category must be provider");
            }
            if (this._errCode == -1) {
                throw new InvalidArgumentException("The error code must be provider");
            }
        }

        @Override
        protected void beforeCreateInstance() {
            // do nothing
        }

        @Override
        protected void afterCreateInstance() {
            // do nothing
        }
    }
}