"use strict";
const book = document.querySelector("#book");
book.addEventListener("input", () => {
  const title = book.value.trim() || "책이름";
  document.querySelectorAll("[data-number]").forEach(file => {
    file.textContent = `${title}_${file.dataset.number}.png`;
  });
});
