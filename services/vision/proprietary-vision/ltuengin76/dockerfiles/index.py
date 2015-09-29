import web

urls = (
  '/', 'index'
)

class index:
    def GET(self):
        return "LTU 7.6.3 is running !"

if __name__ == "__main__": 
    app = web.application(urls, globals())
    app.run() 
