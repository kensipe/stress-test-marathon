render.twice <- function (fn, file.prefix, width, height) {
    pdf(paste(file.prefix, "pdf", sep="."), width=width, height=height)
    print(fn())
    dev.off()
    svg(paste(file.prefix, "svg", sep="."), width=width, height=height)
    print(fn())
    dev.off()
}
