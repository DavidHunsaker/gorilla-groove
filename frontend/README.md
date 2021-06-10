## Installation

1) Install `npm` / `node`
2) Run `npm install` from the root of the this project (the frontend part)
3) Build the files to serve with `npx webpack` (you may need to add the nodejs folder to your path if npx is not found, or via npm scripts with `npm run build`

You're done. The files are served from the backend server. As long as the backend is running you can see the frontend on localhost:8080

While developing, I recommend running `npx webpack --mode development --watch` and leaving that running. The script `npm run start` will do this, too.
