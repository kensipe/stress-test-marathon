library("ggplot2")
source("common.R")

Sys.setenv(TZ='UTC')
## file <- Sys.getenv("FILE", unset = "./all.tsv")
out_file <- Sys.getenv("OUTFILE", unset = "./output.pdf")

read_step <- function (label) {
    d <- read.table(paste("../output/step-", label, ".tsv", sep=""), sep = "\t", header = T)
    d$run <- rep(label, nrow(d))
    return(d)
}

steps <- sub("step-", "", tools::file_path_sans_ext(dir("../output/", pattern = "tsv$")))

d <- do.call(rbind, lapply(steps, read_step))

d$run <- as.factor(d$run)
d$date <- as.POSIXct( d$date, origin="1970-01-01")
f <- d[d$time > 0.0,]

render.svg(function () {
    return
    (
        ggplot(f) +
        labs(x = "time", y = "response time") +
        scale_y_continuous() +
        geom_point(size = 3, aes(x = date, y = time, colour = run))
    )
}, tools::file_path_sans_ext(out_file), width = 12, height = 6)

means <- aggregate(time ~ run, f, mean)
means$time <- round(means$time, 2)
means

render.svg(function () {
    return
    (
        ggplot(f, aes(x = run, y = time)) +
        labs(x = "time", y = "response time") +
        scale_y_continuous() +
        geom_boxplot(aes(colour = run)) +
        theme(axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) +
        geom_text(data = means, aes(label = time))
    )

}, (paste(tools::file_path_sans_ext(out_file),"_box", sep="")), width = 12, height = 6)

