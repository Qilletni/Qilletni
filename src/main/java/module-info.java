module qilletni.impl {

    exports dev.qilletni.impl;
    exports dev.qilletni.impl.lib;
    exports dev.qilletni.impl.lib.persistence;
    exports dev.qilletni.impl.lang.docs;
    exports dev.qilletni.impl.lang.runner;

    requires qilletni.api;
    requires qilletni.pkgutil;
    requires org.antlr.antlr4.runtime;
    requires org.slf4j;
    
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j.slf4j.impl;
    requires com.google.gson;
    requires java.desktop;
}
