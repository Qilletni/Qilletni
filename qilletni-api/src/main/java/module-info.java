module qilletni.api {
    requires org.jetbrains.annotations;
    exports dev.qilletni.api;
    exports dev.qilletni.api.auth;
    exports dev.qilletni.api.music;
    exports dev.qilletni.api.music.factories;
    
    exports dev.qilletni.api.lang.stack;
    exports dev.qilletni.api.lang.table;
    exports dev.qilletni.api.lang.types;
    exports dev.qilletni.api.lang.types.conversion;
    exports dev.qilletni.api.lang.types.typeclass;
    exports dev.qilletni.api.lang.types.album;
    exports dev.qilletni.api.lang.types.entity;
    exports dev.qilletni.api.lang.types.collection;
    exports dev.qilletni.api.lang.types.song;
    exports dev.qilletni.api.lang.types.list;
    exports dev.qilletni.api.lang.types.weights;
    exports dev.qilletni.api.lang.docs;
    exports dev.qilletni.api.lang.docs.structure;
    exports dev.qilletni.api.lang.docs.structure.item;
    exports dev.qilletni.api.lang.docs.structure.text;
    exports dev.qilletni.api.lang.docs.structure.text.inner;
    exports dev.qilletni.api.lib;
    exports dev.qilletni.api.lib.annotations;
    exports dev.qilletni.api.lib.persistence;
    exports dev.qilletni.api.lib.qll;
    exports dev.qilletni.api.lang.internal;
    exports dev.qilletni.api.lang.internal.debug;
    exports dev.qilletni.api.music.supplier;
    exports dev.qilletni.api.exceptions;
    exports dev.qilletni.api.exceptions.config;
    exports dev.qilletni.api.music.play;
    exports dev.qilletni.api.music.strategies;
    exports dev.qilletni.api.music.strategies.search;
    exports dev.qilletni.api.music.orchestration;
}
