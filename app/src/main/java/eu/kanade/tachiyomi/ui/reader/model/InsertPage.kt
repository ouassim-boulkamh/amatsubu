package eu.kanade.tachiyomi.ui.reader.model

class InsertPage(val parent: ReaderPage) : ReaderPage(parent.index, parent.url, parent.imageUrl) {

    override var chapter: ReaderChapter = parent.chapter

    init {
        status = ReaderPage.State.Ready
        stream = parent.stream
    }
}
