import { Book } from './book.model';
import { BookService } from './book.service';

export class BookListComponent {
    public bookList: Book[] = [];

    constructor(private readonly bookService: BookService) {
    }

    public ngOnInit(): void {
        this.bookList = this.bookService.getBooks();
    }
}
